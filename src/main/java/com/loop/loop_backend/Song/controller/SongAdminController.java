package com.loop.loop_backend.Song.controller;

import com.loop.loop_backend.Artist.dto.ArtistResponseDto;
import com.loop.loop_backend.Artist.service.ArtistService;
import com.loop.loop_backend.Song.DTO.*;
import com.loop.loop_backend.Song.Service.SongService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.CommonResponse;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.infra.itunes.ItunesArtist;
import com.loop.loop_backend.infra.itunes.ItunesClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AD-03 곡 데이터 관리. 전부 관리자 전용(/api/admin/** → hasRole("ADMIN"), SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Song (Admin)", description = "곡 데이터 관리 API (AD-03)")
public class SongAdminController {

    private final SongService songService;
    private final ArtistService artistService;
    private final ItunesClient itunesClient;

    public record ItunesLinkRequest(@NotNull Long itunesArtistId) {}

    @GetMapping("/artists/{artistId}/songs")
    @Operation(summary = "곡 목록 조회", description = "sortOrder 오름차순")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<CommonResponse<List<SongResponse>>> getSongs(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId) {
        return ResponseEntity.ok(CommonResponse.success(songService.getSongs(artistId)));
    }

    @PostMapping("/artists/{artistId}/songs")
    @Operation(summary = "곡 수동 추가", description = "trackId 없이 추가. sortOrder = 해당 아티스트 최대값 + 1")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<CommonResponse<SongResponse>> createSong(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId,
            @Valid @RequestBody SongCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(songService.createSong(artistId, request)));
    }

    @PutMapping("/songs/{songId}")
    @Operation(summary = "곡 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "곡 없음")
    })
    public ResponseEntity<CommonResponse<SongResponse>> updateSong(
            @Parameter(description = "곡 PK") @PathVariable Long songId,
            @Valid @RequestBody SongUpdateRequest request) {
        return ResponseEntity.ok(CommonResponse.success(songService.updateSong(songId, request)));
    }

    @DeleteMapping("/songs/{songId}")
    @Operation(summary = "곡 삭제", description = "소프트 삭제. 이후 iTunes 재불러오기 시에도 되살리지 않음")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "곡 없음")
    })
    public ResponseEntity<Void> deleteSong(
            @Parameter(description = "곡 PK") @PathVariable Long songId) {
        songService.deleteSong(songId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/itunes/artists")
    @Operation(summary = "iTunes 아티스트 후보 검색", description = "country=JP")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공"),
            @ApiResponse(responseCode = "502", description = "iTunes 연동 오류")
    })
    public ResponseEntity<CommonResponse<List<ItunesArtist>>> searchItunesArtists(
            @Parameter(description = "검색어") @RequestParam String query) {
        return ResponseEntity.ok(CommonResponse.success(itunesClient.searchArtists(query)));
    }

    @PutMapping("/artists/{artistId}/itunes")
    @Operation(summary = "아티스트 ↔ iTunes 연결", description = "iTunes에서 실제로 조회되는 아티스트 ID만 연결")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "연결 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음 / iTunes 아티스트 없음"),
            @ApiResponse(responseCode = "502", description = "iTunes 연동 오류")
    })
    public ResponseEntity<CommonResponse<ArtistResponseDto>> linkItunes(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId,
            @Valid @RequestBody ItunesLinkRequest request) {
        return ResponseEntity.ok(CommonResponse.success(
                artistService.linkItunes(artistId, request.itunesArtistId())));
    }

    @PostMapping("/artists/{artistId}/songs/fetch")
    @Operation(summary = "iTunes 곡 불러오기",
            description = "trackId 기준 upsert. 기존 곡 titleKo 유지, 소프트 삭제 곡은 skip, 원문 제목이 같은 다른 버전은 처음 것만 저장")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "불러오기 성공 (created/updated/skipped/duplicated)"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음"),
            @ApiResponse(responseCode = "409", description = "iTunes 미연결 아티스트"),
            @ApiResponse(responseCode = "502", description = "iTunes 연동 오류")
    })
    public ResponseEntity<CommonResponse<SongFetchResult>> fetchSongs(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId) {
        return ResponseEntity.ok(CommonResponse.success(songService.fetchSongs(artistId)));
    }

    @GetMapping("/artists/{artistId}/songs/csv")
    @Operation(summary = "곡 CSV 다운로드", description = "UTF-8 BOM. 컬럼: trackId, 원문, 로마자, 한글, sortOrder")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "다운로드 성공"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<byte[]> exportCsv(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"songs-" + artistId + ".csv\"")
                .body(songService.exportCsv(artistId));
    }

    @PostMapping(value = "/artists/{artistId}/songs/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "곡 CSV 업로드", description = "trackId로 매칭해 한글 곡명만 반영. 빈 값은 유지, 매칭 안 된 행은 리포트")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공 (결과 리포트)"),
            @ApiResponse(responseCode = "400", description = "빈 파일 / CSV 형식 오류"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<CommonResponse<CsvImportResult>> importCsv(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId,
            @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new BusinessException(ErrorCode.EMPTY_FILE);
        return ResponseEntity.ok(CommonResponse.success(songService.importCsv(artistId, file.getInputStream())));
    }
}
