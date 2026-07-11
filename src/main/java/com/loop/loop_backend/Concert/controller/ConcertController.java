package com.loop.loop_backend.Concert.controller;

import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.kopis.KopisSyncService;
import com.loop.loop_backend.Concert.service.ConcertService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/concerts")
@RequiredArgsConstructor
@Tag(name = "Concert", description = "콘서트 관리 API")
public class ConcertController {

    private final ConcertService concertService;
    private final KopisSyncService kopisSyncService;

    @PostMapping
    @Operation(summary = "콘서트 등록", description = "새 콘서트를 등록합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패")
    })
    public ResponseEntity<CommonResponse<ConcertResponseDto>> createConcert(
            @Valid @RequestBody ConcertRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(concertService.createConcert(requestDto)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "콘서트 수정", description = "콘서트 정보를 수정합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<ConcertResponseDto>> updateConcert(
            @Parameter(description = "콘서트 PK") @PathVariable Long id,
            @Valid @RequestBody ConcertRequestDto requestDto) {
        return ResponseEntity.ok(CommonResponse.success(concertService.updateConcert(id, requestDto)));
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
    @Operation(summary = "전체 콘서트 조회", description = "모든 콘서트 목록을 반환합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<ConcertResponseDto>>> getAllConcerts() {
        return ResponseEntity.ok(CommonResponse.success(concertService.getAllConcerts()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "콘서트 조회 (PK)", description = "PK로 콘서트를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "콘서트 없음")
    })
    public ResponseEntity<CommonResponse<ConcertResponseDto>> getConcertById(
            @Parameter(description = "콘서트 PK") @PathVariable Long id) {
        return ResponseEntity.ok(CommonResponse.success(concertService.getConcertById(id)));
    }

    @GetMapping("/search")
    @Operation(summary = "콘서트 검색 (제목)", description = "제목 키워드로 콘서트를 검색합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<ConcertResponseDto>>> searchConcerts(
            @Parameter(description = "검색 키워드") @RequestParam String title) {
        return ResponseEntity.ok(CommonResponse.success(concertService.searchConcertsByTitle(title)));
    }

    @GetMapping("/artist/{artistId}")
    @Operation(summary = "아티스트별 콘서트 조회", description = "특정 아티스트의 콘서트 목록을 반환합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<CommonResponse<List<ConcertResponseDto>>> getConcertsByArtist(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId) {
        return ResponseEntity.ok(CommonResponse.success(concertService.getConcertsByArtist(artistId)));
    }

    @PostMapping("/sync")
    @Operation(summary = "KOPIS 동기화 수동 트리거", description = "DB의 모든 아티스트를 대상으로 KOPIS 공연 정보를 즉시 동기화합니다")
    @ApiResponse(responseCode = "200", description = "동기화 완료")
    public ResponseEntity<CommonResponse<String>> syncFromKopis() {
        kopisSyncService.syncAll();
        return ResponseEntity.ok(CommonResponse.success("KOPIS 동기화 완료"));
    }
}
