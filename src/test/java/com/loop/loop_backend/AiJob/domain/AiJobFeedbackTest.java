package com.loop.loop_backend.AiJob.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 처리 결과(피드백) 약속 기준.
 * - AI 결과가 있는(DONE) 작업에만 남긴다.
 * - finalValue는 적용 후 수정(EDITED)한 최종값이다. 다른 처리 결과에는 남기지 않는다.
 * - 피드백은 공연 저장을 막지 않는 부가 기록이라, 긴 최종값은 거절하지 않고 잘라 남긴다.
 */
class AiJobFeedbackTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

    private AiJob jobIn(AiJobStatus status) {
        AiJob job = AiJob.create(AiJobType.CONCERT_ENRICH, 31L, null, "{}", 1L);
        ReflectionTestUtils.setField(job, "status", status);
        return job;
    }

    @Test
    void 결과가_있는_작업에만_피드백을_남길_수_있다() {
        assertThat(jobIn(AiJobStatus.DONE).canReceiveFeedback()).isTrue();

        assertThat(jobIn(AiJobStatus.PENDING).canReceiveFeedback()).isFalse();
        assertThat(jobIn(AiJobStatus.RUNNING).canReceiveFeedback()).isFalse();
        assertThat(jobIn(AiJobStatus.FAILED).canReceiveFeedback()).isFalse();
        assertThat(jobIn(AiJobStatus.CANCELED).canReceiveFeedback()).isFalse();
    }

    @Test
    void 적용_후_수정한_항목은_최종값을_남긴다() {
        AiJobFeedback feedback = AiJobFeedback.create(1L, "showtime.2026-12-05", AiJobFeedbackOutcome.EDITED, "18:00", 7L);

        assertThat(feedback.getItemKey()).isEqualTo("showtime.2026-12-05");
        assertThat(feedback.getOutcome()).isEqualTo(AiJobFeedbackOutcome.EDITED);
        assertThat(feedback.getFinalValue()).isEqualTo("18:00");
        assertThat(feedback.getCreatedBy()).isEqualTo(7L);
    }

    @Test
    void 수정하지_않은_처리_결과에는_최종값을_남기지_않는다() {
        for (AiJobFeedbackOutcome outcome : AiJobFeedbackOutcome.values()) {
            if (outcome == AiJobFeedbackOutcome.EDITED) continue;

            AiJobFeedback feedback = AiJobFeedback.create(1L, "generalSales.0.opens", outcome, "10.22 12:00", 7L);

            assertThat(feedback.getFinalValue()).as(outcome.name()).isNull();
        }
    }

    @Test
    void 긴_최종값은_거절하지_않고_잘라서_남긴다() {
        String longUrl = "https://example.com/" + "a".repeat(600);

        AiJobFeedback feedback = AiJobFeedback.create(1L, "link.officialSiteUrl", AiJobFeedbackOutcome.EDITED, longUrl, 7L);

        assertThat(feedback.getFinalValue()).hasSize(500);
        assertThat(longUrl).startsWith(feedback.getFinalValue());
    }
}
