package com.loop.loop_backend.AiJob.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * AI 결과 항목 하나에 대한 관리자 처리 결과. 어드민이 저장에 성공했을 때 작업 1건당 한 번 보내고,
 * 다시 저장하면 그 작업의 피드백을 통째로 바꾼다.
 *
 * <p>item_key는 어드민 화면의 행 식별자(예: generalSales.0.opens, d1-0, songs.3)를 그대로 저장한다.
 * Backend는 형식을 해석하지 않는다 - 화면이 키 규칙을 바꿔도 Backend를 고치지 않게.
 * job_id에 FK를 걸지 않는다(다른 AI 작업 테이블과 같은 원칙). (job_id, item_key)는 유일하다.
 */
@Entity
@Table(name = "ai_job_feedback",
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_job_feedback_item", columnNames = {"job_id", "item_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJobFeedback {

    public static final int KEY_MAX_LENGTH = 100;
    public static final int FINAL_VALUE_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "item_key", nullable = false, length = KEY_MAX_LENGTH)
    private String itemKey;

    // varchar 명시 - 네이티브 enum이면 값을 추가할 때 기존 테이블에 저장되지 않는다(AiJob.status와 같은 이유)
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, columnDefinition = "varchar(20)")
    private AiJobFeedbackOutcome outcome;

    // EDITED일 때만. 관리자가 최종 저장한 값
    @Column(name = "final_value", length = FINAL_VALUE_MAX_LENGTH)
    private String finalValue;

    // 피드백을 보낸 관리자 ID
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private AiJobFeedback(Long jobId, String itemKey, AiJobFeedbackOutcome outcome, String finalValue, Long createdBy) {
        this.jobId = jobId;
        this.itemKey = itemKey;
        this.outcome = outcome;
        this.finalValue = finalValue;
        this.createdBy = createdBy;
    }

    /**
     * finalValue는 "고쳐서 저장한 값"이라 EDITED일 때만 남긴다. 너무 길면 자른다 -
     * 피드백은 공연 저장 뒤 따로 보내는 부가 기록이라, 길이 때문에 거절하면 피드백 전체를 잃는다.
     */
    public static AiJobFeedback create(Long jobId, String itemKey, AiJobFeedbackOutcome outcome,
                                       String finalValue, Long createdBy) {
        String kept = outcome == AiJobFeedbackOutcome.EDITED ? truncate(finalValue) : null;
        return new AiJobFeedback(jobId, itemKey, outcome, kept, createdBy);
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= FINAL_VALUE_MAX_LENGTH) {
            return value;
        }
        return value.substring(0, FINAL_VALUE_MAX_LENGTH);
    }
}
