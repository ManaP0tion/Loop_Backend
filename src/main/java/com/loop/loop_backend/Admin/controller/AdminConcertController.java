package com.loop.loop_backend.Admin.controller;

import com.loop.loop_backend.Admin.controller.AdminController.PageResp;
import com.loop.loop_backend.Admin.service.AdminAccessLogService;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertCreateRequest;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertRow;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertDetailResponse;
import com.loop.loop_backend.Concert.dto.admin.AdminConcertUpdateRequest;
import com.loop.loop_backend.Concert.service.AdminConcertService;
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
 * 공연 목록·등록·수정(AD-01). 공연 삭제·포스터 업로드·KOPIS 수집은 아직 AdminController에 있다.
 * 흐름: (선별 대기 승인 또는 직접 등록) → 비공개 공연 → 상세 조회·수정 → 수정 화면의 토글로 공개 전환.
 */
@RestController
@RequestMapping("/api/admin/concerts")
@RequiredArgsConstructor
@Tag(name = "Admin Concert", description = "공연 관리 (ADMIN 전용)")
public class AdminConcertController {

    private static final String INVALID_INPUT_EXAMPLE =
            "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}";
    private static final String CONCERT_NOT_FOUND_EXAMPLE =
            "{\"success\":false,\"message\":\"콘서트를 찾을 수 없습니다.\",\"code\":404}";

    private final AdminConcertService adminConcertService;
    private final AdminAccessLogService accessLog;

    @GetMapping
    @Operation(summary = "등록된 공연 목록",
            description = "공연 관리 페이지의 등록된 공연 목록. 공개·비공개 공연을 모두 보여주고 최근 등록순이다.\n\n" +
                    "- q: 공연명, 공연명 별칭, 아티스트 이름(별칭 포함), 장소(공연장 이름)에서 부분 일치 검색(대소문자 무시)\n" +
                    "- category: 공연 유형으로 거른다 (J_POP_ARTIST 내한 / JAPAN_FESTIVAL 페스티벌)\n" +
                    "- published: true 공개만 / false 비공개만 / 없으면 전체\n\n" +
                    "ticketScheduled(예매 등록 여부)는 예매 일시가 입력된 선예매·일반예매가 하나라도 있으면 true다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    public ResponseEntity<CommonResponse<PageResp<AdminConcertRow>>> list(
            @Parameter(description = "검색어(공연명·별칭·아티스트·장소). 비우면 전체", example = "YUURI")
            @RequestParam(required = false) String q,
            @Parameter(description = "공연 유형. 비우면 전체", example = "J_POP_ARTIST")
            @RequestParam(required = false) ConcertCategory category,
            @Parameter(description = "공개 여부. 비우면 전체", example = "false")
            @RequestParam(required = false) Boolean published,
            @Parameter(description = "페이지 번호(0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(CommonResponse.success(
                PageResp.from(adminConcertService.search(q, category, published, pageable))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "공연 상세 (수정 화면용)",
            description = "관리자 입력 항목을 모두 돌려준다. showtimes는 공연 기간의 모든 DAY가 순서대로 나오고 미정인 DAY는 startTime이 null이다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "공연 없음",
                    content = @Content(examples = @ExampleObject(value = CONCERT_NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<AdminConcertDetailResponse>> get(
            @Parameter(description = "공연 id") @PathVariable Long id) {
        return ResponseEntity.ok(CommonResponse.success(adminConcertService.get(id)));
    }

    @PostMapping
    @Operation(summary = "공연 직접 등록",
            description = "KOPIS에 없는 공연용. **항상 비공개로 생성**된다. 포스터는 생성 후 POST /{id}/image로 올리고, " +
                    "필수값을 채운 뒤 수정 API에서 published=true로 공개한다.\n\n" +
                    "**필수**: category, title. 공연 시각(showtimes)은 DAY 순서대로 시각만 보내며 개수는 공연 일수와 같아야 한다. " +
                    "예매 정보(presales, generalSales)도 함께 보낼 수 있다(블록마다 예매 일시 + 예매처, 예매처 이름 필수).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "등록 성공 (비공개)"),
            @ApiResponse(responseCode = "400",
                    description = "필수값 누락, 국내 유형 선택, 시작일 > 종료일, 공연 시각 개수 불일치, 예매처 이름 누락, 페스티벌에 아티스트·예상 곡 수, " +
                            "숙소 노출인데 딥링크 없음 등 - 필드 구분 없이 하나로 응답",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "없는 공연장·아티스트")
    })
    public ResponseEntity<CommonResponse<AdminConcertDetailResponse>> create(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Valid @RequestBody AdminConcertCreateRequest body) {
        AdminConcertDetailResponse created = adminConcertService.create(body);
        accessLog.log(adminId, req, "CREATE_CONCERT", "CONCERT", created.id(), "공연 직접 등록: " + created.title());
        return ResponseEntity.ok(CommonResponse.success(created));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "공연 부분 수정 (공개 전환 포함)",
            description = "**보낸 필드만 바뀐다.** null이거나 보내지 않은 필드는 변경 없음(다른 PATCH API와 같은 규칙).\n\n" +
                    "| 필드 종류 | 보내면 | null·안 보냄 |\n" +
                    "|---|---|---|\n" +
                    "| 값 (title, venueId 등) | 그 값으로 변경 | 유지 |\n" +
                    "| 목록 (titleAliases, productCodes, showtimes, artistIds, presales, generalSales) | 통째로 교체, `[]`는 비움 | 유지 |\n" +
                    "| lodgingUrl | `\"\"`이면 비움 | 유지 |\n\n" +
                    "**공개 전환**: 수정 화면의 토글 값을 published로 보낸다. **저장 결과가 공개 상태면** 필수값" +
                    "(유형·공연명·포스터·기간·공연장, 내한 공연이면 아티스트 1명)을 검사하고, 빠지면 400. 비공개는 빈 칸이 있어도 저장된다.\n\n" +
                    "**공연 시각**: DAY 순서대로 시각만 보낸다(개수 = 공연 일수). 기간만 바꾸고 시각을 안 보내면 같은 날짜의 시각은 유지, " +
                    "새 기간 밖은 삭제, 새로 생긴 DAY는 미정.\n\n" +
                    "**예매 정보(presales, generalSales)**: 수정 화면의 저장 버튼 하나로 함께 저장한다. 블록마다 " +
                    "`{ opensAt, vendors: [{ name, url }] }` - 예매 일시는 미정이면 null, 예매처 이름은 필수, 링크는 선택. " +
                    "목록을 보내면 기존 블록을 모두 지우고 새로 만들어서 블록 id가 바뀐다.\n\n" +
                    "**유형 변경**: 페스티벌로 바꾸면 아티스트·예상 곡 수가 비워진다.\n\n" +
                    "포스터는 POST /{id}/image로만 바꾼다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400",
                    description = "공개 상태인데 필수값 누락, 공연명 빈 값, 국내 유형 선택, 시작일 > 종료일, 공연 시각 개수 불일치, 예매처 이름 누락, " +
                            "페스티벌에 아티스트·예상 곡 수, 숙소 노출인데 딥링크 없음 등 - 필드 구분 없이 하나로 응답",
                    content = @Content(examples = @ExampleObject(value = INVALID_INPUT_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "없는 공연·공연장·아티스트",
                    content = @Content(examples = @ExampleObject(value = CONCERT_NOT_FOUND_EXAMPLE)))
    })
    public ResponseEntity<CommonResponse<AdminConcertDetailResponse>> update(
            @AuthenticationPrincipal Long adminId, HttpServletRequest req,
            @Parameter(description = "공연 id") @PathVariable Long id,
            @Valid @RequestBody AdminConcertUpdateRequest body) {
        AdminConcertDetailResponse updated = adminConcertService.update(id, body);
        accessLog.log(adminId, req, "UPDATE_CONCERT", "CONCERT", id,
                "공연 수정: " + updated.title() + (updated.published() ? " (공개)" : " (비공개)"));
        return ResponseEntity.ok(CommonResponse.success(updated));
    }
}