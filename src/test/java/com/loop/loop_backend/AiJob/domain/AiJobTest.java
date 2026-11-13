package com.loop.loop_backend.AiJob.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 작업 연동 약속(3절 결과를 받는 조건, 5절 시간 초과) 기준.
 * 워커 A가 10:00에 claim(attempt 1, lease 10:10까지)한 상황을 기본으로 둔다.
 */
class AiJobTest {

    private static final String A = "pm-macbook";
    private static final String B = "ec2-worker";
    private static final LocalDateTime T10_00 = LocalDateTime.of(2026, 11, 20, 10, 0);

    private AiJob newJob() {
        return AiJob.create(AiJobType.CONCERT_ENRICH, 31L, "{\"concertId\":31}", 1L);
    }

    /**
     * 명세의 claim 결과를 그대로 만든다: RUNNING, workerId 기록, leaseUntil = 지금 + 10분, attempt + 1.
     * claim은 리포지토리의 조건부 UPDATE로 하므로(엔티티 메서드 없음) 테스트에서는 그 결과 상태를 직접 둔다.
     */
    private void claim(AiJob job, String workerId, LocalDateTime now) {
        ReflectionTestUtils.setField(job, "status", AiJobStatus.RUNNING);
        ReflectionTestUtils.setField(job, "workerId", workerId);
        ReflectionTestUtils.setField(job, "leaseUntil", now.plusMinutes(10));
        ReflectionTestUtils.setField(job, "attempt", job.getAttempt() + 1);
    }

    private AiJob claimedByAAt10() {
        AiJob job = newJob();
        claim(job, A, T10_00);
        return job;
    }

    @Nested
    class 작업_생성 {

        @Test
        void 새_작업은_PENDING이고_시도_횟수는_0이다() {
            AiJob job = newJob();

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
            assertThat(job.getAttempt()).isZero();
            assertThat(job.isInProgress()).isTrue();
        }

        @Test
        void 아무도_가져가지_않은_작업에_온_결과는_받지_않는다() {
            AiJob job = newJob();

            assertThat(job.canAcceptResult(A, 0)).isFalse();
            assertThat(job.canAcceptResult(A, 1)).isFalse();
        }
    }

    @Nested
    class 결과를_받는_조건 {

        @Test
        void 상황1_lease_안에_담당_워커가_보고하면_받는다() {
            AiJob job = claimedByAAt10();

            assertThat(job.canAcceptResult(A, 1)).isTrue();
        }

        @Test
        void 상황2_lease가_지났어도_아직_정리되지_않아_RUNNING이면_받는다() {
            AiJob job = claimedByAAt10();
            // 10:11 - lease는 지났지만 정리 작업이 아직 안 돌았다

            assertThat(job.isLeaseOver(T10_00.plusMinutes(11))).isTrue();
            assertThat(job.canAcceptResult(A, 1)).isTrue();
        }

        @Test
        void 상황3_PENDING으로_되돌려졌지만_아직_아무도_안_가져갔으면_직전_담당의_결과를_받아_DONE이_된다() {
            AiJob job = claimedByAAt10();
            job.expireLease(T10_00.plusMinutes(11));

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
            assertThat(job.canAcceptResult(A, 1)).isTrue();

            job.complete("{\"status\":\"FOUND\"}", T10_00.plusMinutes(12));

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.DONE);
            assertThat(job.getResult()).isEqualTo("{\"status\":\"FOUND\"}");
            assertThat(job.isInProgress()).isFalse();
        }

        @Test
        void 상황4_다른_워커가_다시_가져갔으면_이전_워커의_결과는_받지_않는다() {
            AiJob job = claimedByAAt10();
            job.expireLease(T10_00.plusMinutes(11));
            claim(job, B, T10_00.plusMinutes(12));

            assertThat(job.canAcceptResult(A, 1)).isFalse();
            assertThat(job.canAcceptResult(B, 2)).isTrue();
        }

        @Test
        void 상황5_같은_워커가_재시작해_다시_가져갔으면_옛_시도의_결과는_받지_않는다() {
            AiJob job = claimedByAAt10();
            job.expireLease(T10_00.plusMinutes(11));
            claim(job, A, T10_00.plusMinutes(12));

            assertThat(job.canAcceptResult(A, 1)).isFalse();
            assertThat(job.canAcceptResult(A, 2)).isTrue();
        }

        @Test
        void 상황6_두_번째_시도도_시간_초과로_끝났어도_그_뒤에_온_담당_워커의_결과는_받아_DONE으로_덮어쓴다() {
            AiJob job = claimedByAAt10();
            job.expireLease(T10_00.plusMinutes(11));
            claim(job, A, T10_00.plusMinutes(12));
            job.expireLease(T10_00.plusMinutes(23));

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED);
            assertThat(job.canAcceptResult(A, 2)).isTrue();

            job.complete("{\"status\":\"NOT_FOUND\"}", T10_00.plusMinutes(24));

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.DONE);
            assertThat(job.getErrorCode()).isNull();
            assertThat(job.getErrorMessage()).isNull();
            assertThat(job.getErrorRetryable()).isNull();
        }

        @Test
        void 상황7_이미_DONE이면_다시_받지_않는다() {
            AiJob job = claimedByAAt10();
            job.complete("{\"status\":\"FOUND\"}", T10_00.plusMinutes(1));

            assertThat(job.canAcceptResult(A, 1)).isFalse();
        }

        @Test
        void 상황7_워커가_보고한_실패는_덮어쓰지_않는다() {
            AiJob job = claimedByAAt10();
            job.fail("LLM_TIMEOUT", "AI 분석 시간이 초과됐습니다.", true, T10_00.plusMinutes(1));

            assertThat(job.canAcceptResult(A, 1)).isFalse();
        }

        @Test
        void 담당이_아닌_워커의_결과는_받지_않는다() {
            AiJob job = claimedByAAt10();

            assertThat(job.canAcceptResult(B, 1)).isFalse();
        }
    }

    @Nested
    class 워커가_보고한_실패 {

        @Test
        void 실패_코드와_안내_문구와_다시_시도_가능_여부를_남긴다() {
            AiJob job = claimedByAAt10();

            job.fail("INVALID_PAYLOAD", "요청 형식이 올바르지 않습니다.", false, T10_00.plusMinutes(1));

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("INVALID_PAYLOAD");
            assertThat(job.getErrorMessage()).isEqualTo("요청 형식이 올바르지 않습니다.");
            assertThat(job.getErrorRetryable()).isFalse();
            assertThat(job.isInProgress()).isFalse();
        }

        @Test
        void 워커가_보고한_실패는_시간_초과로_끝난_것과_구분된다() {
            AiJob job = claimedByAAt10();

            job.fail("LLM_TIMEOUT", "AI 분석 시간이 초과됐습니다.", true, T10_00.plusMinutes(1));

            assertThat(job.isLeaseExpired()).isFalse();
        }
    }

    @Nested
    class 시간_초과_정리 {

        @Test
        void lease가_지나지_않았으면_그대로_둔다() {
            AiJob job = claimedByAAt10();

            job.expireLease(T10_00.plusMinutes(9));

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.RUNNING);
            assertThat(job.getWorkerId()).isEqualTo(A);
        }

        @Test
        void 첫_시도가_시간_초과면_PENDING으로_되돌리고_담당_워커와_시도_횟수는_남긴다() {
            AiJob job = claimedByAAt10();

            job.expireLease(T10_00.plusMinutes(11));

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.PENDING);
            assertThat(job.getWorkerId()).isEqualTo(A);
            assertThat(job.getAttempt()).isEqualTo(1);
            assertThat(job.isInProgress()).isTrue();
        }

        @Test
        void 두_번째_시도도_시간_초과면_다시_시도할_수_있는_LEASE_EXPIRED_실패로_끝낸다() {
            AiJob job = claimedByAAt10();
            job.expireLease(T10_00.plusMinutes(11));
            claim(job, B, T10_00.plusMinutes(12));

            job.expireLease(T10_00.plusMinutes(23));

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("LEASE_EXPIRED");
            assertThat(job.getErrorRetryable()).isTrue();
            assertThat(job.getErrorMessage()).isNotBlank();
            assertThat(job.isLeaseExpired()).isTrue();
            assertThat(job.getWorkerId()).isEqualTo(B);
            assertThat(job.getAttempt()).isEqualTo(2);
        }

        @Test
        void 끝난_작업은_건드리지_않는다() {
            AiJob job = claimedByAAt10();
            job.complete("{\"status\":\"FOUND\"}", T10_00.plusMinutes(1));

            job.expireLease(T10_00.plusMinutes(30));

            assertThat(job.getStatus()).isEqualTo(AiJobStatus.DONE);
        }
    }
}