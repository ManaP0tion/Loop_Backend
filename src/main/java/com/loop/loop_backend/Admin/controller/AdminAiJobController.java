package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.AiJob.domain.AiJobType;
import com.loop.loop_backend.AiJob.dto.AdminAiJobCreateRequest;
import com.loop.loop_backend.AiJob.dto.AdminAiJobCreateResponse;
import com.loop.loop_backend.AiJob.dto.AdminAiJobFeedbackRequest;
import com.loop.loop_backend.AiJob.dto.AdminAiJobResponse;
import com.loop.loop_backend.AiJob.service.AiJobFeedbackService;
import com.loop.loop_backend.AiJob.service.AiJobService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 어드민 "AI로 찾기". 작업을 만들면 AI 워커가 가져가 처리하고(20~60초), 화면은 상태를 조회해 결과로 입력란을 미리 채운다.
 * 저장은 관리자가 확인한 뒤 기존 API로 한다 - 이 API는 결과를 공연에 반영하지 않는다.
 */
@RestController
@RequestMapping("/api/admin/ai-jobs")
@RequiredArgsConstructor
@Tag(name = "Admin AI Job", description = "AI로 티켓 정보·페스티벌 라인업 찾기 (ADMIN 전용). " +
        "작업을 만들고 2~3초마다 상태를 조회한다. DONE이면 result로 화면을 미리 채우고, 저장은 관리자 확인 후 기존 API로 한다. " +
        "2분 넘게 PENDING이면 AI 워커가 꺼져 있을 수 있다.")
public class AdminAiJobController {

    private static final String UNSUPPORTED_EXAMPLE =
            "{\"success\":false,\"message\":\"아직 지원하지 않는 AI 작업입니다.\",\"code\":400}";
    private static final String NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"AI 작업을 찾을 수 없습니다.\",\"code\":404}";
    private static final String ALREADY_FINISHED_EXAMPLE =
            "{\"success\":false,\"message\":\"이미 끝난 작업입니다.\",\"code\":409}";

    private final AiJobService aiJobService;
    private final AiJobFeedbackService aiJobFeedbackService;
    private final AdminAccessLogService accessLog;

    @PostMapping
    @Operation(summary = "AI 작업 만들기",
            description = "| type | 대상 | 결과 |\n" +
                    "|---|---|---|\n" +
                    "| `CONCERT_ENRICH` | 모든 공연 | 티켓 정보(선예매·일반 예매 등) |\n" +
                    "| `FESTIVAL_LINEUP` | 페스티벌, 시작일 있음 | DAY별 라인업 |\n" +
                    "| `SETLIST_ACTUAL` | 출연 정보 1개(`performanceId` 필수). 페스티벌은 헤드라이너만, 시작일 있음 | 실제 셋리스트(곡 순서, Loop 곡이면 songId) |\n\n" +
                    "같은 공연·종류(셋리스트는 출연 정보까지)의 작업이 대기 중이거나 처리 중이면 새로 만들지 않고 그 작업 ID를 돌려준다(유료 호출 중복 방지). " +
                    "셋리스트 결과는 자동 저장하지 않는다 - 첫 저장 때 결과 메일이 나가므로 관리자가 확인 후 기존 셋리스트 저장 API로 저장한다. " +
                    "실패 후 다시 시도하려면 이 API를 다시 부른다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성(또는 진행 중인 작업) ID"),
            @ApiResponse(responseCode = "400",
                    description = "AI_JOB_FESTIVAL_ONLY(단독 공연에 라인업), AI_JOB_START_DATE_REQUIRED(라인업·셋리스트인데 시작일 없음), " +
                            "AI_JOB_PERFORMANCE_REQUIRED(셋리스트인데 performanceId 없음), " +
                            "AI_JOB_PERFORMANCE_NOT_IN_CONCERT(그 공연의 출연 정보가 아님), AI_JOB_HEADLINER_ONLY(페스티벌 헤드라이너가 아님)",
                    content = @Content(examples = @ExampleObject(value = UNSUPPORTED_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연 없음")
    })
    public ResponseEntity<CommonResponse<AdminAiJobCreateResponse>> create(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Valid @RequestBody AdminAiJobCreateRequest body) {
        AdminAiJobCreateResponse result = aiJobService.create(adminId, body);
        accessLog.log(adminId, req, "CREATE_AI_JOB", "CONCERT", body.concertId(),
                "AI 작업 요청: " + body.type() + performanceLabel(body.performanceId()) + " (jobId " + result.jobId() + ")");
        return ResponseEntity.ok(CommonResponse.success(result));
    }

    @PostMapping("/{jobId}/cancel")
    @Operation(summary = "AI 작업 취소",
            description = "대기(PENDING)·처리 중(RUNNING) 작업만 취소한다. 워커가 꺼져 있어 계속 대기 중이거나 잘못 만든 작업을 끝낼 때 쓴다. " +
                    "처리 중이던 작업은 워커가 이미 AI를 호출했을 수 있고(비용 발생), 그 뒤 도착한 결과는 받지 않는다. " +
                    "취소 후 같은 공연·종류로 새 작업을 만들 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "취소됨(status: CANCELED)"),
            @ApiResponse(responseCode = "404", description = "없는 작업",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "이미 끝난 작업(DONE·FAILED·CANCELED)",
                    content = @Content(examples = @ExampleObject(value = ALREADY_FINISHED_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<AdminAiJobResponse>> cancel(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "작업 ID") @PathVariable Long jobId) {
        AdminAiJobResponse result = aiJobService.cancel(adminId, jobId);
        accessLog.log(adminId, req, "CANCEL_AI_JOB", "CONCERT", result.concertId(),
                "AI 작업 취소: " + result.type() + performanceLabel(result.performanceId()) + " (jobId " + jobId + ")");
        return ResponseEntity.ok(CommonResponse.success(result));
    }

    @PostMapping("/{jobId}/feedback")
    @Operation(summary = "AI 결과 처리 결과(피드백) 저장",
            description = "AI 결과로 채운 화면을 **저장에 성공한 직후** 한 번 보낸다(닫기·취소 때는 보내지 않는다). " +
                    "다시 저장하면 다시 보내고, 그 작업의 피드백을 통째로 바꾼다. 결과가 있는(DONE) 작업만 받는다.\n\n" +
                    "| outcome | 뜻 |\n|---|---|\n" +
                    "| `MATCHED` | AI 값이 기존 값과 같음 |\n" +
                    "| `APPLIED` | AI 값을 그대로 적용해 저장 |\n" +
                    "| `EDITED` | 적용 후 고쳐서 저장(finalValue에 최종값, 선택) |\n" +
                    "| `NOT_APPLIED` | 채울 수 있었지만 적용하지 않음 |\n" +
                    "| `KEPT_EXISTING` | 기존(KOPIS) 값과 달라 기존 값 유지 |\n\n" +
                    "이 요청이 실패해도 공연·라인업·셋리스트 저장은 이미 끝난 상태다 - 화면은 실패를 무시해도 된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장(교체)됨"),
            @ApiResponse(responseCode = "400", description = "items 없음, key 없음·100자 초과·중복, 정해지지 않은 outcome"),
            @ApiResponse(responseCode = "404", description = "없는 작업",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "결과가 없는 작업(DONE이 아님)")
    })
    public ResponseEntity<CommonResponse<Void>> feedback(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "작업 ID") @PathVariable Long jobId,
            @Valid @RequestBody AdminAiJobFeedbackRequest body) {
        int saved = aiJobFeedbackService.replace(adminId, jobId, body);
        accessLog.log(adminId, req, "AI_JOB_FEEDBACK", "AI_JOB", jobId, "AI 결과 처리 결과 " + saved + "건 저장");
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @GetMapping("/latest")
    @Operation(summary = "공연·종류의 마지막 AI 작업",
            description = "화면에 들어오면 먼저 부른다 - 새로고침했거나 다른 관리자가 찾은 결과도 다시 찾지 않고(유료 호출) 쓴다. " +
                    "상태와 관계없이 가장 최근 작업 1건을 상태 조회와 같은 형식으로 준다(셋리스트는 출연 정보별로 따로). " +
                    "DONE이면 result로 미리 채우고(finishedAt 시점의 결과), PENDING·RUNNING이면 그 jobId로 상태 조회를 이어 가고, " +
                    "FAILED면 error를 보여 준다. 한 번도 찾은 적 없으면 204.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "가장 최근 작업"),
            @ApiResponse(responseCode = "204", description = "이 공연·종류로 만든 작업이 없음"),
            @ApiResponse(responseCode = "400", description = "concertId·type 누락, 없는 type, " +
                    "SETLIST_ACTUAL인데 performanceId 없음(AI_JOB_PERFORMANCE_REQUIRED)")
    })
    public ResponseEntity<CommonResponse<AdminAiJobResponse>> latest(
            @Parameter(description = "공연 ID") @RequestParam Long concertId,
            @Parameter(description = "작업 종류") @RequestParam AiJobType type,
            @Parameter(description = "출연 정보 ID. SETLIST_ACTUAL에서 필수, 다른 종류는 무시")
            @RequestParam(required = false) Long performanceId) {
        return aiJobService.findLatest(concertId, type, performanceId)
                .map(job -> ResponseEntity.ok(CommonResponse.success(job)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{jobId}")
    @Operation(summary = "AI 작업 상태",
            description = "PENDING(대기) → RUNNING(처리 중) → DONE(result 있음) / FAILED(error 있음). " +
                    "error.retryable이 true면 \"다시 시도\" 버튼을 보여 새 작업을 만든다. " +
                    "LEASE_EXPIRED는 워커(PM PC)가 켜져 있는지 확인이 필요하다는 뜻이다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "없는 작업",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<AdminAiJobResponse>> get(
            @Parameter(description = "작업 ID") @PathVariable Long jobId) {
        return ResponseEntity.ok(CommonResponse.success(aiJobService.get(jobId)));
    }

    private static String performanceLabel(Long performanceId) {
        return performanceId == null ? "" : " performanceId " + performanceId;
    }
}
