package com.loop.loop_backend.Artist.controller;

import com.loop.loop_backend.Artist.dto.ArtistRequestDto;
import com.loop.loop_backend.Artist.dto.ArtistResponseDto;
import com.loop.loop_backend.Artist.service.ArtistService;
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
@RequestMapping("/api/artists")
@RequiredArgsConstructor
@Tag(name = "Artist", description = "아티스트 관리 API")
public class ArtistController {

    private final ArtistService artistService;

    @PostMapping
    @Operation(summary = "아티스트 등록", description = "새 아티스트를 등록합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "등록 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패")
    })
    public ResponseEntity<CommonResponse<ArtistResponseDto>> createArtist(
            @Valid @RequestBody ArtistRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(artistService.createArtist(requestDto)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "아티스트 수정", description = "아티스트 정보를 수정합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<CommonResponse<ArtistResponseDto>> updateArtist(
            @Parameter(description = "아티스트 PK") @PathVariable Long id,
            @Valid @RequestBody ArtistRequestDto requestDto) {
        return ResponseEntity.ok(CommonResponse.success(artistService.updateArtist(id, requestDto)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "아티스트 삭제", description = "아티스트를 삭제합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<Void> deleteArtist(
            @Parameter(description = "아티스트 PK") @PathVariable Long id) {
        artistService.deleteArtist(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "전체 아티스트 조회", description = "모든 아티스트 목록을 반환합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<ArtistResponseDto>>> getAllArtists() {
        return ResponseEntity.ok(CommonResponse.success(artistService.getAllArtists()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "아티스트 조회 (PK)", description = "PK로 아티스트를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<CommonResponse<ArtistResponseDto>> getArtistById(
            @Parameter(description = "아티스트 PK") @PathVariable Long id) {
        return ResponseEntity.ok(CommonResponse.success(artistService.getArtistById(id)));
    }

    @GetMapping("/search")
    @Operation(summary = "아티스트 검색", description = "이름(서비스명/기본명칭/한국어/별칭) 통합 검색")
    @ApiResponse(responseCode = "200", description = "검색 성공")
    public ResponseEntity<CommonResponse<List<ArtistResponseDto>>> searchArtists(
            @Parameter(description = "검색어") @RequestParam String query) {
        return ResponseEntity.ok(CommonResponse.success(artistService.searchArtists(query)));
    }
}
