package com.loop.loop_backend.AiJob.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.AiJob.domain.AiJob;
import com.loop.loop_backend.AiJob.domain.AiJobStatus;
import com.loop.loop_backend.AiJob.domain.AiJobType;
import com.loop.loop_backend.AiJob.dto.AdminAiJobCreateRequest;
import com.loop.loop_backend.AiJob.dto.AdminAiJobCreateResponse;
import com.loop.loop_backend.AiJob.dto.AdminAiJobResponse;
import com.loop.loop_backend.AiJob.dto.AiJobClaimResponse;
import com.loop.loop_backend.AiJob.dto.AiJobResultRequest;
import com.loop.loop_backend.AiJob.repository.AiJobRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * AI 작업 생성(어드민) · 배정(claim) · 결과 보고 · 조회 · 시간 초과 정리.
 * 받을지·만료 규칙은 AiJob이 정하고, 여기서는 순서와 트랜잭션을 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobService {

    /** 한 번에 살펴볼 배정 후보 수. 다른 워커가 먼저 가져간 후보는 건너뛴다. */
    private static final int CLAIM_CANDIDATES = 5;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;
    private static final Set<AiJobStatus> IN_PROGRESS = EnumSet.of(AiJobStatus.PENDING, AiJobStatus.RUNNING);

    private final AiJobRepository aiJobRepository;
    private final ConcertRepository concertRepository;
    private final AiJobPayloadBuilder payloadBuilder;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    // ---------- 워커 ----------

    /** 워커가 처리할 수 있는 종류 중 가장 오래된 PENDING 1건을 RUNNING으로 바꿔 넘긴다. 없으면 빈 값(204). */
    @Transactional
    public Optional<AiJobClaimResponse> claim(String workerId, List<String> types) {
        Set<AiJobType> knownTypes = knownTypes(types);
        if (knownTypes.isEmpty()) {
            return Optional.empty();
        }
        LocalDateTime now = now();
        List<Long> candidates = aiJobRepository.findClaimCandidateIds(
                AiJobStatus.PENDING, knownTypes, PageRequest.of(0, CLAIM_CANDIDATES));
        for (Long id : candidates) {
            int claimed = aiJobRepository.claim(id, workerId, now, now.plus(AiJob.LEASE_DURATION),
                    AiJobStatus.PENDING, AiJobStatus.RUNNING);
            if (claimed == 1) {
                AiJob job = aiJobRepository.findById(id).orElseThrow();
                log.info("[AiJob] 배정 jobId={} type={} worker={} attempt={}", id, job.getType(), workerId, job.getAttempt());
                return Optional.of(new AiJobClaimResponse(job.getId(), job.getType(), readJson(job.getPayload()), job.getAttempt()));
            }
        }
        return Optional.empty();
    }

    /**
     * 결과 보고. 없는 작업이면 AI_JOB_NOT_FOUND(404), 받을 수 없으면 AI_JOB_NOT_ACCEPTED(409).
     * 같은 순간 시간 초과 정리·claim이 이 작업을 바꿔 version이 충돌하면, 새로 읽어 한 번 더 판단한다
     * - 바로 409를 주면 워커가 돈 들여 만든 결과를 버리기 때문. 두 번째도 충돌하면 500이 되고 워커가 다시 보낸다.
     */
    public void report(Long jobId, AiJobResultRequest request) {
        String resultJson = request.status() == AiJobResultRequest.Outcome.DONE ? writeJson(request.result()) : null;
        try {
            transactionTemplate.executeWithoutResult(status -> applyReport(jobId, request, resultJson));
        } catch (OptimisticLockingFailureException e) {
            log.info("[AiJob] 결과 보고 중 동시 변경 - 다시 판단 jobId={}", jobId);
            transactionTemplate.executeWithoutResult(status -> applyReport(jobId, request, resultJson));
        }
    }

    private void applyReport(Long jobId, AiJobResultRequest request, String resultJson) {
        AiJob job = aiJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
        if (!job.canAcceptResult(request.workerId(), request.attempt())) {
            log.info("[AiJob] 결과 거절 jobId={} status={} 담당={}/{} 보고={}/{}", jobId, job.getStatus(),
                    job.getWorkerId(), job.getAttempt(), request.workerId(), request.attempt());
            throw new BusinessException(ErrorCode.AI_JOB_NOT_ACCEPTED);
        }
        LocalDateTime now = now();
        if (request.status() == AiJobResultRequest.Outcome.DONE) {
            job.complete(resultJson, now);
        } else {
            AiJobResultRequest.Error error = request.error();
            job.fail(error.code(), truncate(error.message()), error.retryable(), now);
        }
        log.info("[AiJob] 결과 저장 jobId={} status={}", jobId, job.getStatus());
    }

    /**
     * lease가 지난 RUNNING 작업 정리. 작업마다 따로 저장해, 한 건이 결과 보고와 겹쳐 충돌해도 나머지는 처리한다.
     * 충돌한 작업은 결과 보고가 이긴 것이라 그대로 두거나 다음 주기에 다시 본다.
     */
    public void expireLeases() {
        LocalDateTime now = now();
        for (Long id : aiJobRepository.findLeaseOverIds(AiJobStatus.RUNNING, now)) {
            try {
                transactionTemplate.executeWithoutResult(status -> aiJobRepository.findById(id).ifPresent(job -> {
                    job.expireLease(now);
                    log.warn("[AiJob] 시간 초과 jobId={} worker={} attempt={} -> {}",
                            id, job.getWorkerId(), job.getAttempt(), job.getStatus());
                }));
            } catch (OptimisticLockingFailureException e) {
                log.info("[AiJob] 시간 초과 정리 중 동시 변경 - 건너뜀 jobId={}", id);
            }
        }
    }

    // ---------- 어드민 ----------

    /**
     * 어드민 작업 생성. payload는 공연 DB 값으로 만든다.
     * 같은 공연·종류의 작업이 대기 중이거나 처리 중이면 새로 만들지 않고 그 작업의 ID를 돌려준다(유료 호출 중복 방지).
     */
    @Transactional
    public AdminAiJobCreateResponse create(Long adminId, AdminAiJobCreateRequest request) {
        AiJobType type = request.type();
        Concert concert = concertRepository.findById(request.concertId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
        validateTarget(type, concert);

        Optional<AiJob> inProgress = aiJobRepository.findFirstByTypeAndConcertIdAndStatusInOrderByIdDesc(
                type, concert.getId(), IN_PROGRESS);
        if (inProgress.isPresent()) {
            return new AdminAiJobCreateResponse(inProgress.get().getId());
        }

        String payload = writeJson(payloadBuilder.build(type, concert));
        AiJob job = aiJobRepository.save(AiJob.create(type, concert.getId(), payload, adminId));
        log.info("[AiJob] 생성 jobId={} type={} concertId={} admin={}", job.getId(), type, concert.getId(), adminId);
        return new AdminAiJobCreateResponse(job.getId());
    }

    /** 어드민 상태 조회. 없으면 AI_JOB_NOT_FOUND(404). */
    @Transactional(readOnly = true)
    public AdminAiJobResponse get(Long jobId) {
        AiJob job = aiJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
        JsonNode result = job.getResult() == null ? null : readJson(job.getResult());
        return AdminAiJobResponse.of(job, result);
    }

    // 잘못된 요청으로 유료 호출이 나가지 않도록 워커에 넘기기 전에 거른다
    private void validateTarget(AiJobType type, Concert concert) {
        if (!type.isAdminCreatable()) {
            throw new BusinessException(ErrorCode.AI_JOB_UNSUPPORTED_TYPE);
        }
        if (type == AiJobType.FESTIVAL_LINEUP) {
            if (concert.getCategory() != ConcertCategory.FESTIVAL) {
                throw new BusinessException(ErrorCode.AI_JOB_FESTIVAL_ONLY);
            }
            // 라인업은 시작일을 DAY 1로 날짜 → DAY 번호를 계산한다
            if (concert.getStartDate() == null) {
                throw new BusinessException(ErrorCode.AI_JOB_START_DATE_REQUIRED);
            }
        }
    }

    // ---------- 공통 ----------

    // 모르는 종류는 버린다 - 워커가 새 종류를 먼저 배포해도 아는 종류의 작업은 계속 받게
    private static Set<AiJobType> knownTypes(List<String> types) {
        Set<AiJobType> known = EnumSet.noneOf(AiJobType.class);
        for (String type : types) {
            for (AiJobType candidate : AiJobType.values()) {
                if (candidate.name().equals(type)) {
                    known.add(candidate);
                }
            }
        }
        return known;
    }

    private static String truncate(String message) {
        return message.length() <= ERROR_MESSAGE_MAX_LENGTH ? message : message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 작업 JSON 직렬화 실패", e);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("AI 작업 JSON 역직렬화 실패", e);
        }
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }
}