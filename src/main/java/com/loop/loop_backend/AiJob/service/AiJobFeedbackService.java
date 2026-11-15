package com.loop.loop_backend.AiJob.service;

import com.loop.loop_backend.AiJob.domain.AiJob;
import com.loop.loop_backend.AiJob.domain.AiJobFeedback;
import com.loop.loop_backend.AiJob.dto.AdminAiJobFeedbackRequest;
import com.loop.loop_backend.AiJob.repository.AiJobFeedbackRepository;
import com.loop.loop_backend.AiJob.repository.AiJobRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자 처리 결과(피드백) 저장. 정확도는 여기서 계산하지 않는다 - 저장된 AI 결과(result)의 review와 합쳐 나중에 계산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobFeedbackService {

    private final AiJobRepository aiJobRepository;
    private final AiJobFeedbackRepository feedbackRepository;

    /**
     * 그 작업의 피드백을 통째로 바꾼다. 없는 작업이면 AI_JOB_NOT_FOUND(404), 결과가 없는 작업이면 AI_JOB_FEEDBACK_NOT_DONE(409).
     * 작업 행을 잠그고 바꾼다 - 저장 버튼 연타로 두 요청이 동시에 오면 지우기·넣기가 섞일 수 있어 차례로 처리한다.
     */
    @Transactional
    public int replace(Long adminId, Long jobId, AdminAiJobFeedbackRequest request) {
        AiJob job = aiJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
        if (!job.canReceiveFeedback()) {
            throw new BusinessException(ErrorCode.AI_JOB_FEEDBACK_NOT_DONE);
        }

        int removed = feedbackRepository.deleteByJobId(jobId);
        List<AiJobFeedback> feedbacks = request.items().stream()
                .map(item -> AiJobFeedback.create(jobId, item.key(), item.outcome(), item.finalValue(), adminId))
                .toList();
        feedbackRepository.saveAll(feedbacks);
        log.info("[AiJob] 피드백 저장 jobId={} type={} items={} (기존 {}건 교체) admin={}",
                jobId, job.getType(), feedbacks.size(), removed, adminId);
        return feedbacks.size();
    }
}
