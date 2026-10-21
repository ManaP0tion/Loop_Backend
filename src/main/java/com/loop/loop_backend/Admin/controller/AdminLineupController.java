package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Lineup.dto.LineupItemResponse;
import com.loop.loop_backend.Lineup.dto.LineupAddRequest;
import com.loop.loop_backend.Lineup.dto.LineupPatchRequests;
import com.loop.loop_backend.Lineup.service.LineupService;
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

import java.util.List;

/**
 * 라인업 관리(AD-04) - 페스티벌 전용. 모든 변경은 즉시 저장되고 응답은 항상 그 공연의 라인업 전체(노출 순서대로)다.
 */
@RestController
@RequestMapping("/api/admin/concerts/{concertId}/lineup")
@RequiredArgsConstructor
@Tag(name = "Admin Lineup", description = "페스티벌 라인업 관리 (ADMIN 전용). " +
        "모든 변경은 즉시 저장되고, 응답은 항상 그 공연의 라인업 전체(노출 순서대로)다. " +
        "헤드라이너는 예상 셋리스트를 운영하고, lineupId가 셋리스트 API의 performanceId다. " +
        "노출 순서가 페스티벌 페이지 라인업 전체 탭에 그대로 반영된다.")
public class AdminLineupController {

    private static final String INVALID_INPUT_EXAMPLE =
            "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}";
    private static final String NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"라인업 항목을 찾을 수 없습니다.\",\"code\":404}";
    private static final String DUPLICATE_EXAMPLE =
            "{\"success\":false,\"message\":\"이미 같은 DAY 라인업에 있는 아티스트입니다.\",\"code\":409}";
    private static final String SETLIST_DATA_EXAMPLE =
            "{\"success\":false,\"message\":\"셋리스트나 투표가 있는 출연 정보는 지우거나 헤드라이너를 해제할 수 없습니다.\",\"code\":409}";

    private final LineupService lineupService;
    private final AdminAccessLogService accessLog;

    @GetMapping
    @Operation(summary = "라인업 목록", description = "노출 순서대로 전체. DAY 탭은 각 항목의 days로 프론트에서 거른다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (라인업이 없으면 빈 목록)"),
            @ApiResponse(responseCode = "404", description = "공연 없음")
    })
    public ResponseEntity<CommonResponse<List<LineupItemResponse>>> list(
            @Parameter(description = "공연 PK") @PathVariable Long concertId) {
        return ResponseEntity.ok(CommonResponse.success(lineupService.list(concertId)));
    }

    @PostMapping
    @Operation(summary = "라인업 추가",
            description = "두 방식 중 하나만 보낸다. 추가한 항목은 맨 뒤에 붙는다.\n\n" +
                    "| 방식 | 요청 |\n" +
                    "|---|---|\n" +
                    "| DB 선택 | `{\"day\": 1, \"artistIds\": [3, 7]}` |\n" +
                    "| 직접 입력 | `{\"day\": 1, \"name\": \"Vaundy\", \"imageUrl\": \"https://...\", \"category\": \"DOMESTIC_ARTIST\"}` - 아티스트 DB에도 저장된다. category 생략 시 J_POP_ARTIST |\n\n" +
                    "이미 라인업에 있는 아티스트를 다른 DAY로 추가하면 새 항목이 생기지 않고 그 항목의 `days`에 DAY가 더해진다" +
                    "(셋리스트·투표는 아티스트당 하나). 같은 DAY에 이미 있으면 409이고 아무것도 추가되지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추가 성공"),
            @ApiResponse(responseCode = "400",
                    description = "페스티벌이 아님, 공연 기간 미정, DAY 범위 밖, artistIds와 name을 둘 다 보내거나 둘 다 안 보냄, category에 페스티벌 값",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연 또는 아티스트 없음"),
            @ApiResponse(responseCode = "409", description = "같은 DAY에 이미 있는 아티스트",
                    content = @Content(examples = @ExampleObject(value = DUPLICATE_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<List<LineupItemResponse>>> add(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Valid @RequestBody LineupAddRequest body) {
        List<LineupItemResponse> result = lineupService.add(concertId, body);
        accessLog.log(adminId, req, "ADD_LINEUP", "CONCERT", concertId, "라인업 추가: DAY" + body.day());
        return ResponseEntity.ok(CommonResponse.success(result));
    }

    @PutMapping("/{lineupId}/days")
    @Operation(summary = "출연 DAY 변경",
            description = "출연 DAY 목록을 통째로 바꾼다. 같은 아티스트는 공연당 항목 하나이고, 셋리스트·투표도 그 항목에 하나다(DAY를 바꿔도 그대로).\n\n" +
                    "빈 목록이나 공연 기간 밖 DAY는 400. 출연을 없애려면 항목을 삭제한다. " +
                    "아티스트 이름·이미지 수정은 아티스트 관리 화면에서 한다(라인업은 아티스트 값을 그대로 쓴다).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "빈 목록, DAY 범위 밖",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연 또는 라인업 항목 없음",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<List<LineupItemResponse>>> replaceDays(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "라인업 항목 PK") @PathVariable Long lineupId,
            @Valid @RequestBody LineupPatchRequests.Days body) {
        List<LineupItemResponse> result = lineupService.replaceDays(concertId, lineupId, body.days());
        accessLog.log(adminId, req, "UPDATE_LINEUP", "CONCERT", concertId, "라인업 출연 DAY 변경: " + lineupId);
        return ResponseEntity.ok(CommonResponse.success(result));
    }

    @DeleteMapping("/{lineupId}")
    @Operation(summary = "라인업 항목 삭제",
            description = "완전히 지운다. 아티스트는 아티스트 DB에 남는다. 셋리스트나 투표가 있는 헤드라이너는 지울 수 없다(409).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "공연 또는 라인업 항목 없음",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "셋리스트·투표가 있는 항목 - PERFORMANCE_HAS_SETLIST_DATA",
                    content = @Content(examples = @ExampleObject(value = SETLIST_DATA_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<List<LineupItemResponse>>> delete(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "라인업 항목 PK") @PathVariable Long lineupId) {
        List<LineupItemResponse> result = lineupService.delete(concertId, lineupId);
        accessLog.log(adminId, req, "DELETE_LINEUP", "CONCERT", concertId, "라인업 삭제: " + lineupId);
        return ResponseEntity.ok(CommonResponse.success(result));
    }

    @PatchMapping("/{lineupId}/order")
    @Operation(summary = "순서 한 칸 이동",
            description = "바로 앞(UP)/뒤(DOWN) 항목과 자리를 바꾼다. 헤드라이너 여부와 상관없이 움직인다. " +
                    "맨 앞에서 UP, 맨 뒤에서 DOWN은 변화 없이 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "이동 성공"),
            @ApiResponse(responseCode = "404", description = "공연 또는 라인업 항목 없음",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<List<LineupItemResponse>>> move(
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "라인업 항목 PK") @PathVariable Long lineupId,
            @Valid @RequestBody LineupPatchRequests.Order body) {
        return ResponseEntity.ok(CommonResponse.success(lineupService.move(concertId, lineupId, body.direction())));
    }

    @PatchMapping("/{lineupId}/expected-song-count")
    @Operation(summary = "헤드라이너 예상 곡 수",
            description = "헤드라이너 예상 셋리스트의 n(최대 선택 곡 수·하이라이트 개수·적중률 상위 n곡). " +
                    "null이면 비움 - 셋리스트에서 20곡으로 처리. 헤드라이너가 아니면 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "헤드라이너 아님, 1 미만",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연 또는 라인업 항목 없음",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<List<LineupItemResponse>>> changeExpectedSongCount(
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "라인업 항목 PK") @PathVariable Long lineupId,
            @Valid @RequestBody LineupPatchRequests.ExpectedSongCount body) {
        return ResponseEntity.ok(CommonResponse.success(
                lineupService.changeExpectedSongCount(concertId, lineupId, body.expectedSongCount())));
    }

    @PatchMapping("/{lineupId}/headliner")
    @Operation(summary = "헤드라이너 지정/해제",
            description = "DAY당 개수 제한 없음. 헤드라이너만 예상 셋리스트를 운영한다 - " +
                    "셋리스트나 투표가 있는 헤드라이너는 해제할 수 없다(409).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 성공"),
            @ApiResponse(responseCode = "404", description = "공연 또는 라인업 항목 없음",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "셋리스트·투표가 있는 헤드라이너 해제 - PERFORMANCE_HAS_SETLIST_DATA",
                    content = @Content(examples = @ExampleObject(value = SETLIST_DATA_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<List<LineupItemResponse>>> changeHeadliner(
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "라인업 항목 PK") @PathVariable Long lineupId,
            @Valid @RequestBody LineupPatchRequests.Headliner body) {
        return ResponseEntity.ok(CommonResponse.success(
                lineupService.changeHeadliner(concertId, lineupId, body.headliner())));
    }
}
