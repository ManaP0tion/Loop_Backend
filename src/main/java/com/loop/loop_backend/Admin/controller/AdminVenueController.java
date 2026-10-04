package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.controller.AdminController.PageResp;
import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Venue.dto.VenueRequestDto;
import com.loop.loop_backend.Venue.dto.VenueResponseDto;
import com.loop.loop_backend.Venue.dto.VenueUpdateRequestDto;
import com.loop.loop_backend.Venue.service.VenueService;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 공연장 관리(AD-02) - 공연 등록과 분리된 독립 탭.
 * KOPIS 공연을 승인하면 공연장이 자동으로 만들어지고(ConcertImportService), 관리자는 여기서
 * KOPIS에 없는 값(좌석 시야·지도 링크)을 채우거나 KOPIS에 없는 공연장을 직접 등록한다.
 */
@RestController
@RequestMapping("/api/admin/venues")
@RequiredArgsConstructor
@Tag(name = "Admin Venue", description = "공연장 관리 (ADMIN 전용). " +
        "KOPIS 공연을 승인하면 공연장이 자동으로 만들어져 연결되고, 여기서는 KOPIS에 없는 값(좌석 시야·지도 링크)을 채우거나 " +
        "KOPIS에 없는 공연장을 직접 등록한다. 공연장을 수정하면 연결된 공연에 바로 반영된다.")
public class AdminVenueController {

    private static final String INVALID_INPUT_EXAMPLE =
            "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}";
    private static final String VENUE_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"공연장을 찾을 수 없습니다.\",\"code\":404}";

    private final VenueService venueService;
    private final AdminAccessLogService accessLog;

    @GetMapping
    @Operation(summary = "공연장 목록",
            description = "최근 등록순. q가 있으면 공연장명 일부로 검색한다(대소문자 무시). " +
                    "공연 등록 화면의 공연장 선택 목록에도 쓴다 - 같은 이름의 공연장이 있을 수 있으니 주소를 함께 보여준다. " +
                    "각 공연장에 연결된 공연 수(concertCount)가 함께 나온다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<CommonResponse<PageResp<VenueResponseDto>>> list(
            @Parameter(description = "공연장명 검색어(일부 일치). 비우면 전체", example = "KSPO")
            @RequestParam(required = false) String q,
            @Parameter(description = "페이지 번호(0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(CommonResponse.success(PageResp.from(venueService.search(q, pageable))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "공연장 상세", description = "수정 화면용. 연결된 공연 수(concertCount)가 함께 나온다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "공연장 없음",
                    content = @Content(examples = @ExampleObject(value = VENUE_NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<VenueResponseDto>> get(
            @Parameter(description = "공연장 PK") @PathVariable Long id) {
        return ResponseEntity.ok(CommonResponse.success(venueService.get(id)));
    }

    @PostMapping
    @Operation(summary = "공연장 직접 등록",
            description = "KOPIS에 없는 공연장용 - KOPIS 공연을 승인하면 공연장은 자동으로 만들어진다.\n\n" +
                    "**필수값**: name(공연장명), address(주소, 시·구 단위). 나머지는 생략하거나 null로 보내면 비어 있는 채로 등록된다.\n\n" +
                    "같은 이름의 공연장도 등록할 수 있다. 좌표와 KOPIS 시설·홀 ID는 받지 않는다(KOPIS 자동 생성 때만 채워짐).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공 (concertCount는 0)"),
            @ApiResponse(responseCode = "400",
                    description = "필수값(name, address) 누락·빈 값, 수용 인원 음수, 길이 초과 - 필드 구분 없이 하나로 응답",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<VenueResponseDto>> create(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Valid @RequestBody VenueRequestDto body) {
        VenueResponseDto created = venueService.create(body);
        accessLog.log(adminId, req, "CREATE_VENUE", "VENUE", created.id(), "공연장 등록: " + created.name());
        return ResponseEntity.ok(CommonResponse.success(created));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "공연장 부분 수정",
            description = "**보낸 필드만 바뀐다.** 다른 PATCH API와 같은 규칙으로 null이면 변경 없음이다. " +
                    "자동 생성된 공연장의 좌석 시야·지도 링크를 채울 때 쓴다. 연결된 공연에 바로 반영된다.\n\n" +
                    "| 요청 JSON | 결과 |\n" +
                    "|---|---|\n" +
                    "| 필드를 보내지 않음, 또는 `null` | 기존 값 유지 |\n" +
                    "| 값을 보냄 | 그 값으로 변경 |\n" +
                    "| 링크(seatViewUrl, kakaoMapUrl, naverMapUrl)에 `\"\"`(빈 문자열) | 비움 |\n\n" +
                    "**링크를 비우려면 null이 아니라 빈 문자열(\"\")로 보내야 한다** - null은 '유지'로 처리된다.\n\n" +
                    "**필수값**: name, address는 비울 수 없다. null이면 유지되고, 빈 값·공백으로 보내면 400이다.\n\n" +
                    "**수용 인원**은 비울 수 없다. 다른 값으로 바꾸는 것만 된다.\n\n" +
                    "좌표와 KOPIS 시설·홀 ID는 수정할 수 없다(KOPIS 자동 생성 때만 채워짐).\n\n" +
                    "예) 좌석 시야 링크만 넣고 네이버지도 링크는 지우기: `{\"seatViewUrl\": \"https://...\", \"naverMapUrl\": \"\"}`")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400",
                    description = "필수값(name, address)을 빈 값·공백으로 보냄, 수용 인원 음수, 길이 초과 - 필드 구분 없이 하나로 응답",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "공연장 없음",
                    content = @Content(examples = @ExampleObject(value = VENUE_NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<VenueResponseDto>> update(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연장 PK") @PathVariable Long id,
            @Valid @RequestBody VenueUpdateRequestDto body) {
        VenueResponseDto updated = venueService.update(id, body);
        accessLog.log(adminId, req, "UPDATE_VENUE", "VENUE", id, "공연장 수정: " + updated.name());
        return ResponseEntity.ok(CommonResponse.success(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "공연장 삭제",
            description = "연결된 공연이 있어도 삭제된다. 그 공연들은 남고 공연장 연결만 비워진다(null). " +
                    "삭제 전에 상세의 concertCount로 영향받는 공연 수를 안내할 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "공연장 없음",
                    content = @Content(examples = @ExampleObject(value = VENUE_NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<Void>> delete(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연장 PK") @PathVariable Long id) {
        venueService.delete(id);
        accessLog.log(adminId, req, "DELETE_VENUE", "VENUE", id, "공연장 삭제");
        return ResponseEntity.ok(CommonResponse.success(null));
    }
}
