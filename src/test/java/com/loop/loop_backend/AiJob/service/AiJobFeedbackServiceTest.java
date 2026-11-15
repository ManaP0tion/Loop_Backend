package com.loop.loop_backend.AiJob.service;

import com.loop.loop_backend.AiJob.domain.AiJob;
import com.loop.loop_backend.AiJob.domain.AiJobFeedback;
import com.loop.loop_backend.AiJob.domain.AiJobFeedbackOutcome;
import com.loop.loop_backend.AiJob.domain.AiJobStatus;
import com.loop.loop_backend.AiJob.domain.AiJobType;
import com.loop.loop_backend.AiJob.dto.AdminAiJobFeedbackRequest;
import com.loop.loop_backend.AiJob.dto.AdminAiJobFeedbackRequest.Item;
import com.loop.loop_backend.AiJob.repository.AiJobFeedbackRepository;
import com.loop.loop_backend.AiJob.repository.AiJobRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 관리자 처리 결과(피드백) 약속 기준.
 * - 어드민이 저장에 성공했을 때 작업 1건당 한 번 보낸다. 다시 저장하면 그 작업의 피드백을 통째로 바꾼다.
 * - AI 결과가 있는(DONE) 작업에만 남긴다.
 */
@DataJpaTest
class AiJobFeedbackServiceTest {

    private static final Long ADMIN = 7L;

    @Autowired private AiJobRepository aiJobRepository;
    @Autowired private AiJobFeedbackRepository feedbackRepository;
    @Autowired private EntityManager em;

    private AiJobFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new AiJobFeedbackService(aiJobRepository, feedbackRepository);
    }

    private Long jobIn(AiJobStatus status) {
        AiJob job = AiJob.create(AiJobType.CONCERT_ENRICH, 31L, null, "{}", 1L);
        ReflectionTestUtils.setField(job, "status", status);
        return aiJobRepository.save(job).getId();
    }

    private static AdminAiJobFeedbackRequest request(Item... items) {
        return new AdminAiJobFeedbackRequest(List.of(items));
    }

    private List<AiJobFeedback> saved(Long jobId) {
        em.flush();
        em.clear();
        return feedbackRepository.findByJobIdOrderByIdAsc(jobId);
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @Test
    void 결과가_있는_작업의_항목별_처리_결과를_남긴다() {
        Long job = jobIn(AiJobStatus.DONE);

        service.replace(ADMIN, job, request(
                new Item("generalSales.0.opens", AiJobFeedbackOutcome.APPLIED, null),
                new Item("showtime.2026-12-05", AiJobFeedbackOutcome.EDITED, "18:00"),
                new Item("presales.0.vendors", AiJobFeedbackOutcome.KEPT_EXISTING, null)));

        assertThat(saved(job))
                .extracting(AiJobFeedback::getItemKey, AiJobFeedback::getOutcome, AiJobFeedback::getFinalValue,
                        AiJobFeedback::getCreatedBy)
                .containsExactly(
                        tuple("generalSales.0.opens", AiJobFeedbackOutcome.APPLIED, null, ADMIN),
                        tuple("showtime.2026-12-05", AiJobFeedbackOutcome.EDITED, "18:00", ADMIN),
                        tuple("presales.0.vendors", AiJobFeedbackOutcome.KEPT_EXISTING, null, ADMIN));
        assertThat(saved(job)).allSatisfy(f -> assertThat(f.getCreatedAt()).isNotNull());
    }

    @Test
    void 다시_저장하면_그_작업의_피드백을_통째로_바꾼다() {
        Long job = jobIn(AiJobStatus.DONE);
        service.replace(ADMIN, job, request(
                new Item("generalSales.0.opens", AiJobFeedbackOutcome.APPLIED, null),
                new Item("presales.0.opens", AiJobFeedbackOutcome.NOT_APPLIED, null)));

        service.replace(ADMIN, job, request(
                new Item("generalSales.0.opens", AiJobFeedbackOutcome.EDITED, "10.22 14:00"),
                new Item("link.xUrl", AiJobFeedbackOutcome.MATCHED, null)));

        assertThat(saved(job))
                .extracting(AiJobFeedback::getItemKey, AiJobFeedback::getOutcome, AiJobFeedback::getFinalValue)
                .containsExactlyInAnyOrder(
                        tuple("generalSales.0.opens", AiJobFeedbackOutcome.EDITED, "10.22 14:00"),
                        tuple("link.xUrl", AiJobFeedbackOutcome.MATCHED, null));
    }

    @Test
    void 빈_목록을_보내면_이전_피드백이_지워진다() {
        Long job = jobIn(AiJobStatus.DONE);
        service.replace(ADMIN, job, request(new Item("d1-0", AiJobFeedbackOutcome.APPLIED, null)));

        service.replace(ADMIN, job, request());

        assertThat(saved(job)).isEmpty();
    }

    @Test
    void 다른_작업의_피드백은_건드리지_않는다() {
        Long job = jobIn(AiJobStatus.DONE);
        Long other = jobIn(AiJobStatus.DONE);
        service.replace(ADMIN, other, request(new Item("songs.0", AiJobFeedbackOutcome.APPLIED, null)));

        service.replace(ADMIN, job, request(new Item("songs.0", AiJobFeedbackOutcome.NOT_APPLIED, null)));

        assertThat(saved(other)).extracting(AiJobFeedback::getOutcome).containsExactly(AiJobFeedbackOutcome.APPLIED);
        assertThat(saved(job)).extracting(AiJobFeedback::getOutcome).containsExactly(AiJobFeedbackOutcome.NOT_APPLIED);
    }

    @Test
    void 결과가_없는_작업에는_남길_수_없고_기존_피드백도_그대로다() {
        for (AiJobStatus status : List.of(AiJobStatus.PENDING, AiJobStatus.RUNNING, AiJobStatus.FAILED, AiJobStatus.CANCELED)) {
            Long job = jobIn(status);

            assertErrorCode(() -> service.replace(ADMIN, job, request(new Item("d1-0", AiJobFeedbackOutcome.APPLIED, null))),
                    ErrorCode.AI_JOB_FEEDBACK_NOT_DONE);
            assertThat(saved(job)).as(status.name()).isEmpty();
        }
    }

    @Test
    void 없는_작업이면_404() {
        assertErrorCode(() -> service.replace(ADMIN, 999L, request()), ErrorCode.AI_JOB_NOT_FOUND);
    }
}
