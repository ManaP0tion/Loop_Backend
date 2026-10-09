package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Setlist.domain.SetlistType;
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
 * 셋리스트 관리(AD-07) - 단독 공연 전용. 지난 셋리스트(RECENT·PREVIOUS_VISIT)와 실제 셋리스트(ACTUAL)를 같은 방식으로 입력한다.
 * 응답은 항상 그 공연의 셋리스트 전체(최근 공연 → 지난 내한 → 실제 순).
 */
@RestController
@RequestMapping("/api/admin/concerts/{concertId}/setlists")
@RequiredArgsConstructor
@Tag(name = "Admin Setlist", description = "셋리스트 관리 (ADMIN 전용). 단독 공연만. " +
        "지난 셋리스트(RECENT 최근 공연 / PREVIOUS_VISIT 지난 내한, 공연당 최대 2건)와 실제 셋리스트(ACTUAL)를 " +
        "곡 목록에서 골라 순서대로 저장한다. 응답은 항상 그 공연의 셋리스트 전체.")
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
            @ApiResponse(responseCode = "404", description = "공연 없음")
    })
    public ResponseEntity<CommonResponse<List<SetlistResponse>>> list(
            @Parameter(description = "공연 PK") @PathVariable Long concertId) {
        return ResponseEntity.ok(CommonResponse.success(setlistService.list(concertId)));
    }

    @PutMapping("/{type}")
    @Operation(summary = "셋리스트 저장",
            description = "없으면 만들고 있으면 통째로 교체한다. songIds 순서가 곧 공연 순서.\n\n" +
                    "| type | 요청 |\n" +
                    "|---|---|\n" +
                    "| RECENT / PREVIOUS_VISIT | `{\"tourName\": \"...\", \"performedOn\": \"2026-05-01\", \"venueName\": \"Zepp\", \"songIds\": [12, 7]}` - 날짜·장소 필수, 투어명 선택 |\n" +
                    "| ACTUAL | `{\"songIds\": [12, 7, 12]}` - 헤더는 무시. 공연에 예상 곡 수가 있어야 한다 |\n\n" +
                    "곡은 공연 아티스트의 곡만, 같은 곡 중복(앵코르) 허용. 목록에 없는 곡(커버·미발매)은 " +
                    "곡 추가 API(`POST /api/admin/artists/{artistId}/songs`)로 먼저 만들고 그 id를 넣는다 - 다음 공연 후보에도 포함된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400",
                    description = "페스티벌, 아티스트 미지정, 예상 곡 수 없음(ACTUAL), 지난 셋리스트 날짜·장소 누락, " +
                            "곡 없음, 공연 아티스트의 곡이 아님, 잘못된 type",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연 없음")
    })
    public ResponseEntity<CommonResponse<List<SetlistResponse>>> save(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "구분: RECENT / PREVIOUS_VISIT / ACTUAL") @PathVariable SetlistType type,
            @Valid @RequestBody SetlistSaveRequest body) {
        List<SetlistResponse> result = setlistService.save(concertId, type, body);
        accessLog.log(adminId, req, "SAVE_SETLIST", "CONCERT", concertId, "셋리스트 저장: " + type);
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
            @ApiResponse(responseCode = "404", description = "공연 또는 셋리스트 없음",
                    content = @Content(examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<List<SetlistResponse>>> delete(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 PK") @PathVariable Long concertId,
            @Parameter(description = "구분: RECENT / PREVIOUS_VISIT / ACTUAL") @PathVariable SetlistType type) {
        List<SetlistResponse> result = setlistService.delete(concertId, type);
        accessLog.log(adminId, req, "DELETE_SETLIST", "CONCERT", concertId, "셋리스트 삭제: " + type);
        return ResponseEntity.ok(CommonResponse.success(result));
    }
}
