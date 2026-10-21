package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.dto.PreviousActualSetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistResponse;
import com.loop.loop_backend.Setlist.dto.SetlistSaveRequest;
import com.loop.loop_backend.Setlist.service.SetlistService;
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
 * 셋리스트 관리(AD-07) - 출연 정보(단독 공연·페스티벌 헤드라이너) 단위. 지난 셋리스트(RECENT·PREVIOUS_VISIT)와 실제 셋리스트(ACTUAL)를 같은 방식으로 입력한다.
 * 응답은 항상 그 출연 정보의 셋리스트 전체(최근 공연 → 지난 내한 → 실제 순).
 */
@RestController
@RequestMapping("/api/admin/concerts/{concertId}/performances/{performanceId}/setlists")
@RequiredArgsConstructor
@Tag(name = "Admin Setlist", description = "셋리스트 관리 (ADMIN 전용). 출연 정보 단위 - 단독 공연은 공연 상세의 performanceId, 페스티벌은 헤드라이너 라인업의 lineupId. " +
        "지난 셋리스트(RECENT 최근 공연 / PREVIOUS_VISIT 지난 내한, 최대 2건)와 실제 셋리스트(ACTUAL)를 " +
        "곡 목록에서 골라 순서대로 저장한다. 응답은 항상 그 출연 정보의 셋리스트 전체.")
public class AdminSetlistController {

    private static final String INVALID_INPUT_EXAMPLE =
            "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}";
    private static final String NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"셋리스트를 찾을 수 없습니다.\",\"code\":404}";

    private final SetlistService setlistService;
    private final AdminAccessLogService accessLog;

    @GetMapping
    @Operation(summary = "셋리스트 목록", description = "최근 공연 → 지난 내한 → 실제 순. 없으면 빈 목록.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "출연 정보 없음 - PERFORMANCE_NOT_FOUND")
    })
    public ResponseEntity<CommonResponse<List<SetlistResponse>>> list(
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "출연 정보 PK") @PathVariable Long performanceId) {
        return ResponseEntity.ok(CommonResponse.success(setlistService.list(concertId, performanceId)));
    }

    @GetMapping("/previous-actuals")
    @Operation(summary = "이전 실제 셋리스트 불러오기",
            description = "지난 셋리스트 입력 화면의 '불러오기'용. 같은 아티스트의 **다른** 공연(단독·페스티벌 헤드라이너, 비공개 포함)에 " +
                    "저장된 실제 셋리스트를 공연일 최신순으로 최대 10건 준다.\n\n" +
                    "고른 항목의 tourName·performedOn·venueName·songs로 입력 폼을 채우고, 관리자가 확인·수정한 뒤 " +
                    "`PUT .../setlists/{RECENT|PREVIOUS_VISIT}`로 저장한다(복사 - 원본과 연결되지 않음). " +
                    "삭제된 곡(deleted=true)은 저장할 수 없으니(400) 폼에서 뺀다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공(없으면 빈 목록)"),
            @ApiResponse(responseCode = "404", description = "출연 정보 없음 - PERFORMANCE_NOT_FOUND")
    })
    public ResponseEntity<CommonResponse<List<PreviousActualSetlistResponse>>> previousActuals(
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "출연 정보 PK") @PathVariable Long performanceId) {
        return ResponseEntity.ok(CommonResponse.success(setlistService.previousActuals(concertId, performanceId)));
    }

    @PutMapping("/{type}")
    @Operation(summary = "셋리스트 저장",
            description = "없으면 만들고 있으면 통째로 교체한다. songIds 순서가 곧 공연 순서.\n\n" +
                    "| type | 요청 |\n" +
                    "|---|---|\n" +
                    "| RECENT / PREVIOUS_VISIT | `{\"tourName\": \"...\", \"performedOn\": \"2026-05-01\", \"venueName\": \"Zepp\", \"songIds\": [12, 7]}` - 날짜·장소 필수, 투어명 선택 |\n" +
                    "| ACTUAL | `{\"songIds\": [12, 7, 12]}` - 헤더는 무시 |\n\n" +
                    "곡은 출연 아티스트의 곡만, 같은 곡 중복(앵코르) 허용. 목록에 없는 곡(커버·미발매)은 " +
                    "곡 추가 API(`POST /api/admin/artists/{artistId}/songs`)로 먼저 만들고 그 id를 넣는다 - 다음 공연 후보에도 포함된다.\n\n" +
                    "곡 데이터에서 삭제된 곡(목록의 deleted=true)은 저장된 셋리스트에는 남지만 새로 저장할 수는 없다 - 다시 저장할 때는 빼야 한다(400).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400",
                    description = "페스티벌 비헤드라이너, 지난 셋리스트 날짜·장소 누락, " +
                            "곡 없음, 출연 아티스트의 곡이 아님, 잘못된 type",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "출연 정보 없음 - PERFORMANCE_NOT_FOUND")
    })
    public ResponseEntity<CommonResponse<List<SetlistResponse>>> save(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "출연 정보 PK") @PathVariable Long performanceId,
            @Parameter(description = "구분: RECENT / PREVIOUS_VISIT / ACTUAL") @PathVariable SetlistType type,
            @Valid @RequestBody SetlistSaveRequest body) {
        List<SetlistResponse> result = setlistService.save(concertId, performanceId, type, body);
        accessLog.log(adminId, req, "SAVE_SETLIST", "CONCERT", concertId, "셋리스트 저장: " + type + " (performanceId=" + performanceId + ")");
        return ResponseEntity.ok(CommonResponse.success(result));
    }

    @DeleteMapping("/{type}")
    @Operation(summary = "지난 셋리스트 삭제",
            description = "RECENT / PREVIOUS_VISIT만. 곡은 곡 목록에 남는다. " +
                    "실제 셋리스트(ACTUAL)는 결과 메일이 최초 저장 때 1회 나가므로 삭제할 수 없고 저장(PUT)으로 교체한다 - 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 type, ACTUAL 삭제 시도",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "출연 정보 또는 셋리스트 없음",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<List<SetlistResponse>>> delete(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "출연 정보 PK") @PathVariable Long performanceId,
            @Parameter(description = "구분: RECENT / PREVIOUS_VISIT / ACTUAL") @PathVariable SetlistType type) {
        List<SetlistResponse> result = setlistService.delete(concertId, performanceId, type);
        accessLog.log(adminId, req, "DELETE_SETLIST", "CONCERT", concertId, "셋리스트 삭제: " + type + " (performanceId=" + performanceId + ")");
        return ResponseEntity.ok(CommonResponse.success(result));
    }
}
