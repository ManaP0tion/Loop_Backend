package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.ConcertImport.dto.KopisSearchResponse;
import com.loop.loop_backend.ConcertImport.dto.ManualImportRequest;
import com.loop.loop_backend.ConcertImport.dto.ManualImportResponse;
import com.loop.loop_backend.ConcertImport.service.ConcertManualImportService;
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
 * KOPIS 직접 등록 - 자동 수집 필터에 걸리지 않는 공연(페스티벌, 필터 밖 내한 공연 등)을 관리자가 공연명으로 찾아 등록한다.
 * 등록은 검토 대기열을 거쳐 바로 승인되므로, 기존 승인과 같은 기본값(공연장·공연 시각·일반예매)으로 비공개 공연이 만들어진다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Concert Import", description = "KOPIS 공연 검색·직접 등록 (ADMIN 전용). " +
        "자동 수집에 안 잡힌 공연을 공연명으로 찾아 등록한다. 등록하면 검토 대기열 승인과 같은 방식으로 비공개 공연이 만들어진다.")
public class AdminConcertImportController {

    private static final String INVALID_INPUT_EXAMPLE =
            "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}";
    private static final String KOPIS_UNAVAILABLE_EXAMPLE =
            "{\"success\":false,\"message\":\"KOPIS 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.\",\"code\":502}";

    private final ConcertManualImportService manualImportService;
    private final AdminAccessLogService accessLog;

    @GetMapping("/kopis/performances")
    @Operation(summary = "KOPIS 공연 검색",
            description = "KOPIS에서 공연명 일부로 검색한다. 기간은 오늘 기준 2년 전 ~ 1년 후, 장르 제한 없음.\n\n" +
                    "- 최대 200건, 시작일 최신순. 200건에 걸리면 truncated=true - 검색어를 더 구체적으로 입력하도록 안내한다\n" +
                    "- 각 결과에 우리 쪽 등록 상태(status)가 붙는다: REGISTERED면 등록 버튼을 막고 concertIds로 공연 수정 화면에 연결한다\n" +
                    "- KOPIS 요청 간격 제한 때문에 몇 초 걸릴 수 있다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (결과가 없으면 빈 목록)"),
            @ApiResponse(responseCode = "400", description = "검색어가 비어 있음",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "502", description = "KOPIS 조회 실패",
                    content = @Content(examples = @ExampleObject(value = KOPIS_UNAVAILABLE_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<KopisSearchResponse>> search(
            @Parameter(description = "공연명 검색어(일부 일치)", example = "Vaundy", required = true)
            @RequestParam String keyword) {
        return ResponseEntity.ok(CommonResponse.success(manualImportService.search(keyword)));
    }

    @PostMapping("/concert-imports/manual")
    @Operation(summary = "KOPIS 공연 직접 등록",
            description = "검색 결과의 kopisId로 공연을 등록한다. 서버가 KOPIS 상세를 다시 조회해 값을 채우고, " +
                    "검토 대기열을 거쳐 **바로 승인**해 비공개 공연을 만든다(기본값은 검토 대기열 승인과 같다).\n\n" +
                    "- category: J_POP_ARTIST 또는 JAPAN_FESTIVAL\n" +
                    "- artistIds: 내한 공연은 0~1명, 페스티벌은 비워야 한다(라인업은 등록 후 라인업 관리에서 추가)\n" +
                    "- 이미 이 KOPIS 공연으로 만든 공연이 있으면 409\n" +
                    "- 검토 대기열에 같은 공연이 대기·거절 상태로 있으면 그 행을 다시 써서 승인한다\n\n" +
                    "등록 후에는 PATCH /api/admin/concerts/{concertId}로 고치고 공개한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "필수값 누락, 고를 수 없는 유형, 아티스트 수 규칙 위반",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "아티스트 없음 또는 KOPIS에 없는 공연",
                    content = @Content(examples = @ExampleObject(value =
                            "{\"success\":false,\"message\":\"KOPIS에서 공연을 찾을 수 없습니다.\",\"code\":404}"))),
            @ApiResponse(responseCode = "409", description = "이미 등록된 공연",
                    content = @Content(examples = @ExampleObject(value =
                            "{\"success\":false,\"message\":\"이미 등록된 공연입니다.\",\"code\":409}"))),
            @ApiResponse(responseCode = "502", description = "KOPIS 조회 실패",
                    content = @Content(examples = @ExampleObject(value = KOPIS_UNAVAILABLE_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<ManualImportResponse>> register(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Valid @RequestBody ManualImportRequest request) {
        ManualImportResponse result = manualImportService.register(request);
        accessLog.log(adminId, req, "MANUAL_IMPORT", "CONCERT", result.concertId(),
                "KOPIS " + request.kopisId() + " 직접 등록 → 콘서트 #" + result.concertId() + " 생성");
        return ResponseEntity.ok(CommonResponse.success(result));
    }
}