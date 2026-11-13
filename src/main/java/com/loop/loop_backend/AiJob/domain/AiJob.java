package com.loop.loop_backend.AiJob.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * AI 워커가 가져가 처리하는 작업 하나.
 * Backend가 AI를 부르지 않고, 워커(PM PC 등 공유기 뒤)가 claim으로 가져가 result로 보고한다.
 *
 * <p>상태 흐름: PENDING → (claim) RUNNING → (결과 보고) DONE / FAILED.
 * RUNNING인데 lease가 지나면 시도 횟수에 따라 PENDING으로 되돌리거나 FAILED(LEASE_EXPIRED)로 끝낸다.
 *
 * <p>claim(PENDING → RUNNING)은 두 워커에 같은 작업을 주지 않도록 리포지토리의 조건부 UPDATE로 한다(엔티티 메서드 없음).
 * 그 UPDATE도 version을 올려, 같은 행을 엔티티로 고치던 쪽(결과 보고·만료 처리)이 충돌을 알아차리게 한다.
 *
 * <p>concert_id에 FK를 걸지 않는다 - 공연 삭제를 막지 않고, 작업 기록은 이력으로 남긴다.
 * payload·result는 Backend가 해석하지 않는 JSON 문자열이다(형식은 AI 서버 API가 정한다).
 */
@Entity
@Table(name = "ai_job", indexes = {
        @Index(name = "idx_ai_job_status_created", columnList = "status, created_at"),
        @Index(name = "idx_ai_job_target", columnList = "type, concert_id, status")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJob {

    /** 워커가 이 시간 안에 결과를 보고해야 한다. 작업 하나가 20~60초라 넉넉히 둔다. */
    public static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    /** 시간 초과로 다시 내주는 것까지 포함한 최대 시도 횟수. 이만큼 시도하고도 시간 초과면 FAILED. */
    public static final int MAX_ATTEMPTS = 2;

    /** Backend만 붙이는 실패 코드. 워커가 직접 보고한 실패와 구분해 늦게 온 결과로 덮어쓸 수 있게 한다. */
    public static final String LEASE_EXPIRED = "LEASE_EXPIRED";
    private static final String LEASE_EXPIRED_MESSAGE = "AI 워커가 시간 안에 결과를 보내지 않았습니다. 워커가 켜져 있는지 확인해 주세요.";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private AiJobType type;

    @Column(name = "concert_id", nullable = false)
    private Long concertId;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiJobStatus status;

    @Lob
    @Column(name = "result")
    private String result;

    // 워커가 새 코드를 추가해도 Backend를 고치지 않도록 enum이 아닌 문자열로 둔다
    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    // 어드민 "다시 시도" 버튼 표시용
    @Column(name = "error_retryable")
    private Boolean errorRetryable;

    // 담당 워커. PENDING으로 되돌려도 지우지 않는다 - 늦게 온 결과가 "직전 담당"의 것인지 확인용
    @Column(name = "worker_id", length = 100)
    private String workerId;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    // 0으로 시작, claim마다 +1. PENDING으로 되돌려도 지우지 않는다 - 늦게 온 결과가 "직전 시도"의 것인지 확인용
    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // 작업을 만든 관리자 ID
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    private AiJob(AiJobType type, Long concertId, String payload, Long createdBy) {
        this.type = type;
        this.concertId = concertId;
        this.payload = payload;
        this.createdBy = createdBy;
        this.status = AiJobStatus.PENDING;
        this.attempt = 0;
    }

    public static AiJob create(AiJobType type, Long concertId, String payload, Long createdBy) {
        return new AiJob(type, concertId, payload, createdBy);
    }

    /**
     * 이 워커의 이 시도 결과를 받을지.
     *
     * <p>담당(또는 직전 담당) 워커이고 시도 번호가 지금과 같아야 한다. 새로 claim되면 attempt가 반드시 오르므로,
     * 둘이 같다는 것만으로 "다른 워커(또는 재시작한 같은 워커)가 지금 잡고 있지 않음"이 보장된다.
     * 그 위에서 결과가 아직 확정되지 않은 상태면 받는다 - 돈 들여 만든 결과라 되도록 버리지 않는다.
     * <ul>
     *   <li>RUNNING: lease가 지났어도 받는다</li>
     *   <li>PENDING: 시간 초과로 되돌려졌지만 아직 아무도 안 가져감</li>
     *   <li>FAILED(LEASE_EXPIRED): "응답 없음"이었을 뿐 실패가 아니다</li>
     * </ul>
     * DONE이거나 워커가 보고한 FAILED는 덮어쓰지 않는다.
     */
    public boolean canAcceptResult(String reportedWorkerId, int reportedAttempt) {
        if (workerId == null || !workerId.equals(reportedWorkerId) || attempt != reportedAttempt) {
            return false;
        }
        return switch (status) {
            case RUNNING, PENDING -> true;
            case FAILED -> isLeaseExpired();
            case DONE -> false;
        };
    }

    /** 결과를 받아 끝낸다. 받을지는 canAcceptResult로 먼저 확인한다. */
    public void complete(String result, LocalDateTime now) {
        this.status = AiJobStatus.DONE;
        this.result = result;
        clearError();
        finish(now);
    }

    /** 워커가 보고한 실패로 끝낸다. 자동으로 다시 돌리지 않는다(매번 유료 호출) - 다시 시도는 어드민이 새 작업으로. */
    public void fail(String errorCode, String errorMessage, boolean retryable, LocalDateTime now) {
        this.status = AiJobStatus.FAILED;
        this.result = null;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorRetryable = retryable;
        finish(now);
    }

    public boolean isLeaseOver(LocalDateTime now) {
        return status == AiJobStatus.RUNNING && leaseUntil != null && leaseUntil.isBefore(now);
    }

    /**
     * lease가 지난 RUNNING 작업 정리(워커가 꺼졌거나 잠자기·죽음).
     * 시도가 남았으면 PENDING으로 되돌려 다른 워커·재시작한 워커가 가져가게 하고, 아니면 FAILED(LEASE_EXPIRED).
     * 어느 쪽이든 worker_id·attempt는 남긴다 - 그 뒤에 도착한 직전 담당의 결과를 받기 위해.
     */
    public void expireLease(LocalDateTime now) {
        if (!isLeaseOver(now)) {
            return;
        }
        this.leaseUntil = null;
        if (attempt < MAX_ATTEMPTS) {
            this.status = AiJobStatus.PENDING;
            return;
        }
        fail(LEASE_EXPIRED, LEASE_EXPIRED_MESSAGE, true, now);
    }

    public boolean isLeaseExpired() {
        return status == AiJobStatus.FAILED && LEASE_EXPIRED.equals(errorCode);
    }

    /** 같은 대상에 새 작업을 만들지 말고 이 작업을 돌려줄지(버튼 연타로 같은 유료 호출이 여러 번 나가는 것 방지). */
    public boolean isInProgress() {
        return status == AiJobStatus.PENDING || status == AiJobStatus.RUNNING;
    }

    private void clearError() {
        this.errorCode = null;
        this.errorMessage = null;
        this.errorRetryable = null;
    }

    private void finish(LocalDateTime now) {
        this.leaseUntil = null;
        this.finishedAt = now;
    }
}