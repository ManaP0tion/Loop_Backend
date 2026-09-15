package com.loop.loop_backend.Concert.controller;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertCategoryDto;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.dto.ConcertSection;
import com.loop.loop_backend.Concert.kopis.KopisSyncService;
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
    private final KopisSyncService kopisSyncService;

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

    @GetMapping
    @Operation(summary = "콘서트 목록 조회",
            description = "section(대분류)과 period(예정/지난)로 조회합니다. 정렬은 서버가 고정합니다 — " +
                    "예정 공연은 가까운 날짜순, 지난 공연은 최근 종료순 (클라이언트가 정렬을 고를 수 없음)")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<ConcertResponseDto>>> getConcerts(
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
    public ResponseEntity<CommonResponse<ConcertResponseDto>> getConcertById(
            @Parameter(description = "콘서트 PK") @PathVariable Long id,
        @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(concertService.getConcertById(id, userId)));
    }

    @GetMapping("/search")
    @Operation(summary = "콘서트 검색",
            description = "키워드로 콘서트를 검색합니다. 콘서트 제목뿐 아니라 아티스트의 원어명/기본명/한글명/별칭도 함께 매칭됩니다. " +
                    "예) 'King Gnu' 공연은 '킹누'로도 검색 가능")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<ConcertResponseDto>>> searchConcerts(
            @Parameter(description = "검색 키워드 (콘서트 제목 또는 아티스트명)") @RequestParam String title,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(concertService.searchConcertsByTitle(title, userId)));
    }

    @GetMapping("/artist/{artistId}")
    @Operation(summary = "아티스트별 콘서트 조회", description = "특정 아티스트의 콘서트 목록을 반환합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<CommonResponse<List<ConcertResponseDto>>> getConcertsByArtist(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(concertService.getConcertsByArtist(artistId, userId)));
    }

    @PostMapping("/sync")
    @Operation(summary = "KOPIS 동기화 수동 트리거", description = "DB의 모든 아티스트를 대상으로 KOPIS 공연 정보를 즉시 동기화합니다")
    @ApiResponse(responseCode = "200", description = "동기화 완료")
    public ResponseEntity<CommonResponse<String>> syncFromKopis() {
        kopisSyncService.syncAll();
        return ResponseEntity.ok(CommonResponse.success("KOPIS 동기화 완료"));
    }
}
