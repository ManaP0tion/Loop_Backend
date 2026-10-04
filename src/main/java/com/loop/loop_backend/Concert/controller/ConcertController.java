package com.loop.loop_backend.Concert.controller;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertCategoryDto;
import com.loop.loop_backend.Concert.dto.ConcertPastDetailDto;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertPeriodDto;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.dto.ConcertSection;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.Concert.dto.ConcertUpcomingDetailDto;
import com.loop.loop_backend.Concert.service.ConcertService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
@Tag(name = "Concert", description = "콘서트 관리 API")
public class ConcertController {

    private final ConcertService concertService;

    // 공연 등록·수정·삭제는 일단 막아 둔다(주석 처리). 관리자 공연 API(/api/admin/concerts, AdminConcertController)가
    // 생기면서 같은 일을 하는 경로가 둘이 됐고, 이 API는 그쪽의 검증 규칙·공연장 연결·예매 정보를 거치지 않는다.
    // 프론트 사용 여부를 확인한 뒤 삭제하거나 되살린다. ConcertService의 create/update/deleteConcert는 그대로 둔다.
    /*
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "콘서트 등록", description = "새 콘서트를 등록합니다. 포스터 이미지 파일을 함께 보내면 " +
            "공개 버킷에 업로드 후 URL이 바로 반영됩니다 (이미지는 선택).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 허용되지 않는 파일 형식/크기 초과")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)))
    public ResponseEntity<CommonResponse<ConcertResponseDto>> createConcert(
            @Valid @RequestPart("request") ConcertRequestDto requestDto,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(concertService.createConcert(requestDto, image)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "콘서트 수정", description = "콘서트 정보를 수정합니다. 포스터 이미지 파일을 함께 보내면 " +
            "새로 업로드하여 교체하고, 보내지 않으면 기존 이미지가 유지됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 허용되지 않는 파일 형식/크기 초과"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)))
    public ResponseEntity<CommonResponse<ConcertResponseDto>> updateConcert(
            @Parameter(description = "콘서트 PK") @PathVariable Long id,
            @Valid @RequestPart("request") ConcertRequestDto requestDto,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        return ResponseEntity.ok(CommonResponse.success(concertService.updateConcert(id, requestDto, image)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "콘서트 삭제", description = "콘서트를 삭제합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<Void> deleteConcert(
            @Parameter(description = "콘서트 PK") @PathVariable Long id) {
        concertService.deleteConcert(id);
        return ResponseEntity.noContent().build();
    }
    */

    @GetMapping
    @Operation(summary = "콘서트 목록 조회",
            description = "section(대분류)과 period(예정/지난)로 조회합니다. 정렬은 서버가 고정합니다 — " +
                    "예정 공연은 가까운 날짜순, 지난 공연은 최근 종료순 (클라이언트가 정렬을 고를 수 없음).\n\n" +
                    "조합별 용도:\n" +
                    "- `section=DOMESTIC_TOUR&period=UPCOMING` : 내한 탭 - 예정 공연 목록 " +
                    "(홈 화면 내한 미리보기도 동일 호출을 재사용)\n" +
                    "- `section=DOMESTIC_TOUR&period=PAST` : 내한 탭 - 지난 공연 목록\n" +
                    "- `section=FESTIVAL&period=UPCOMING` : 페스티벌 탭 - 예정 공연 목록 " +
                    "(홈 화면 페스티벌 미리보기도 동일 호출을 재사용)\n" +
                    "- `section=FESTIVAL&period=PAST` : 페스티벌 탭 - 지난 공연 목록")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<ConcertSummaryDto>>> getConcerts(
            @Parameter(description = "공연 탭 대분류 (DOMESTIC_TOUR 내한 / FESTIVAL 페스티벌)")
            @RequestParam ConcertSection section,
            @Parameter(description = "조회 시점 (UPCOMING 예정 / PAST 지난)")
            @RequestParam ConcertPeriod period,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(
                concertService.getConcertsBySection(section, period, userId)));
    }

    @GetMapping("/categories")
    @Operation(summary = "콘서트 카테고리 목록", description = "선택 가능한 콘서트 카테고리 enum을 반환합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<ConcertCategoryDto>>> getCategories() {
        List<ConcertCategoryDto> categories = Arrays.stream(ConcertCategory.values())
                .map(ConcertCategoryDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(CommonResponse.success(categories));
    }

    @GetMapping("/{id}")
    @Operation(summary = "콘서트 조회 (PK)", description = "PK로 콘서트를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<ConcertSummaryDto>> getConcertById(
            @Parameter(description = "콘서트 PK") @PathVariable Long id,
        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(concertService.getConcertById(id, userId)));
    }

    @GetMapping("/{id}/period")
    @Operation(summary = "콘서트 period 조회",
            description = "URL 직접 접근(딥링크)처럼 프론트가 목록을 안 거쳐서 startDate/endDate를 안 들고 있을 때, " +
                    "id만으로 upcoming-detail/past-detail 중 뭘 호출할지 판단하기 위한 가벼운 조회.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<ConcertPeriodDto>> getPeriod(
            @Parameter(description = "콘서트 PK") @PathVariable Long id) {
        return ResponseEntity.ok(CommonResponse.success(concertService.getPeriod(id)));
    }

    @GetMapping("/{id}/upcoming-detail")
    @Operation(summary = "예정 공연 상세 조회",
            description = "공연 정보와 D-day, 공연 시간 안내(showtime, KOPIS 원문 텍스트), 예매처 목록, 공연장 상세(주소/수용인원/좌표)를 포함한 상세 응답. " +
                    "공연 시간 안내, 예매처 목록, 공연장 정보는 승인 시 KOPIS에서 가져오며, 못 가져왔거나 이 기능 이전에 승인된 공연은 null. " +
                    "선예매 여부/날짜, 일반 예매 날짜, 자리배치도는 어드민 수동 입력 기능이 생기기 전까지 항상 null. " +
                    "id가 가리키는 공연이 실제로 예정 공연이 아니면(이미 지난 공연) 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음 또는 예정 공연이 아님")
    })
    public ResponseEntity<CommonResponse<ConcertUpcomingDetailDto>> getUpcomingDetail(
            @Parameter(description = "콘서트 PK") @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(concertService.getUpcomingDetail(id, userId)));
    }

    @GetMapping("/{id}/past-detail")
    @Operation(summary = "지난 공연 상세 조회",
            description = "공연명/아티스트/공연장/날짜/공연 시간 안내(showtime, KOPIS 원문 텍스트)만 포함하는 간단한 응답 " +
                    "(예매정보는 지난 공연이라 의미 없음). 공연 시간 안내는 못 가져왔거나 이전에 승인된 공연이면 null. " +
                    "id가 가리키는 공연이 실제로 지난 공연이 아니면(예정 공연) 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음 또는 지난 공연이 아님")
    })
    public ResponseEntity<CommonResponse<ConcertPastDetailDto>> getPastDetail(
            @Parameter(description = "콘서트 PK") @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(concertService.getPastDetail(id, userId)));
    }

    @GetMapping("/search")
    @Operation(summary = "콘서트 검색",
            description = "키워드로 콘서트를 검색합니다. 콘서트 제목뿐 아니라 아티스트의 원어명/기본명/한글명/별칭도 함께 매칭됩니다. " +
                    "예) 'King Gnu' 공연은 '킹누'로도 검색 가능\n\n" +
                    "section/period로 검색 범위를 좁힐 수 있습니다 (목록 조회와 동일한 파라미터, 둘 다 생략 가능):\n" +
                    "- 둘 다 생략 : 전체검색 - 카테고리 무관, 예정 목록 뒤에 지난 목록을 이어붙여 반환\n" +
                    "- `section=DOMESTIC_TOUR&period=PAST` : 내한 + 지난\n" +
                    "- `section=DOMESTIC_TOUR&period=UPCOMING` : 내한 + 예정\n" +
                    "- `section=FESTIVAL&period=PAST` : 페스티벌 + 지난\n" +
                    "- `section=FESTIVAL&period=UPCOMING` : 페스티벌 + 예정")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<ConcertSummaryDto>>> searchConcerts(
            @Parameter(description = "검색 키워드 (콘서트 제목 또는 아티스트명)") @RequestParam String title,
            @Parameter(description = "공연 탭 대분류 (DOMESTIC_TOUR 내한 / FESTIVAL 페스티벌, 생략 시 전체)")
            @RequestParam(required = false) ConcertSection section,
            @Parameter(description = "조회 시점 (UPCOMING 예정 / PAST 지난, 생략 시 전체)")
            @RequestParam(required = false) ConcertPeriod period,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(
                concertService.searchConcertsByTitle(title, section, period, userId)));
    }

    @GetMapping("/artist/{artistId}")
    @Operation(summary = "아티스트별 콘서트 조회", description = "특정 아티스트의 콘서트 목록을 반환합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<CommonResponse<List<ConcertSummaryDto>>> getConcertsByArtist(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(concertService.getConcertsByArtist(artistId, userId)));
    }

}
