package com.loop.loop_backend.AiJob.controller;

import com.loop.loop_backend.AiJob.dto.AiJobClaimRequest;
import com.loop.loop_backend.AiJob.dto.AiJobClaimResponse;
import com.loop.loop_backend.AiJob.dto.AiJobResultRequest;
import com.loop.loop_backend.AiJob.service.AiJobService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI 워커 전용 API. 워커는 공유기 뒤(PM PC)에 있어 Backend가 부를 수 없으므로, 워커가 몇 초마다 일감을 가지러 온다.
 * JWT를 타지 않고 X-API-Key 헤더로 인증한다(전용 필터 체인).
 */
@RestController
@RequestMapping("/api/internal/ai-jobs")
@RequiredArgsConstructor
@Tag(name = "AI Worker (internal)", description = "AI 워커 전용. JWT 대신 X-API-Key 헤더로 인증한다. " +
        "워커가 claim으로 작업을 가져가 처리한 뒤 result로 보고한다.")
public class AiJobWorkerController {

    private static final String NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"AI 작업을 찾을 수 없습니다.\",\"code\":404}";
    private static final String NOT_ACCEPTED_EXAMPLE =
            "{\"success\":false,\"message\":\"이 워커의 현재 작업이 아니거나 이미 끝난 작업입니다.\",\"code\":409}";

    private final AiJobService aiJobService;

    @PostMapping("/claim")
    @Operation(summary = "다음 작업 가져오기",
            description = "요청한 types 중에서 가장 오래된 대기 작업 1건을 이 워커에 배정한다(lease 10분). " +
                    "한 작업은 두 워커에 주지 않는다. Backend가 모르는 type은 무시한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "작업 있음 - payload로 처리하고 attempt를 결과 보고 때 그대로 보낸다"),
            @ApiResponse(responseCode = "204", description = "작업 없음 (본문 없음)", content = @Content),
            @ApiResponse(responseCode = "400", description = "workerId 또는 types 없음"),
            @ApiResponse(responseCode = "401", description = "API 키 없음·불일치")
    })
    public ResponseEntity<CommonResponse<AiJobClaimResponse>> claim(@Valid @RequestBody AiJobClaimRequest body) {
        return aiJobService.claim(body.workerId(), body.types())
                .map(job -> ResponseEntity.ok(CommonResponse.success(job)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{jobId}/result")
    @Operation(summary = "결과 보고",
            description = "DONE이면 result(AI 응답 data 그대로), FAILED면 error를 보낸다. result.status가 NOT_FOUND여도 DONE이다.\n\n" +
                    "담당(또는 직전 담당) 워커이고 attempt가 지금 시도와 같으며, 이미 DONE이거나 워커가 보고한 FAILED가 아니면 저장한다. " +
                    "lease가 지났어도 다른 워커가 가져가지 않았으면 받는다. 409면 워커는 결과를 버린다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장함"),
            @ApiResponse(responseCode = "400", description = "형식 오류 - DONE인데 result 없음, FAILED인데 error 없음, LEASE_EXPIRED 코드 보고"),
            @ApiResponse(responseCode = "401", description = "API 키 없음·불일치"),
            @ApiResponse(responseCode = "404", description = "없는 jobId",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "받지 않음 - 다른 시도로 넘어갔거나 이미 끝남",
                    content = @Content(examples = @ExampleObject(value = NOT_ACCEPTED_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<Void>> report(
            @Parameter(description = "작업 ID") @PathVariable Long jobId,
            @Valid @RequestBody AiJobResultRequest body) {
        aiJobService.report(jobId, body);
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}