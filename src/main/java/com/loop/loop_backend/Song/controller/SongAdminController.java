package com.loop.loop_backend.Song.controller;

import com.loop.loop_backend.Artist.dto.ArtistResponseDto;
import com.loop.loop_backend.Artist.service.ArtistService;
import com.loop.loop_backend.Artist.service.ItunesAutoLinkService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final ItunesAutoLinkService itunesAutoLinkService;

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

    @PostMapping(value = "/songs/{songId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "곡 앨범아트 업로드", description = "S3 업로드 후 albumArtUrl 갱신. iTunes 곡은 재불러오기 시 덮어써짐")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(responseCode = "404", description = "곡 없음")
    })
    public ResponseEntity<CommonResponse<SongResponse>> uploadAlbumArt(
            @Parameter(description = "곡 PK") @PathVariable Long songId,
            @RequestPart("image") MultipartFile image) {
        return ResponseEntity.ok(CommonResponse.success(songService.uploadAlbumArt(songId, image)));
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
    @Operation(summary = "아티스트 ↔ iTunes 연결(확인)",
            description = "iTunes에서 실제로 조회되는 아티스트 ID만 연결. 연결하면 itunesAutoLinked=false(관리자 확인 완료). " +
                    "자동 연결된 후보가 맞으면 같은 itunesArtistId로 다시 보내 확인 처리한다")
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

    @PostMapping("/artists/itunes/auto-link")
    @Operation(summary = "iTunes 일괄 자동 연결 시작",
            description = "iTunes 미연결 아티스트 전체를 이름으로 검색해 1순위 후보에 연결(itunesAutoLinked=true, 관리자 확인 대기). " +
                    "이미 연결된 아티스트는 건드리지 않는다. iTunes 호출 제한 때문에 아티스트당 약 3초. 결과는 GET 같은 경로로 확인")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "시작"),
            @ApiResponse(responseCode = "409", description = "이미 실행 중")
    })
    public ResponseEntity<CommonResponse<String>> startItunesAutoLink() {
        if (itunesAutoLinkService.isRunning()) {
            throw new BusinessException(ErrorCode.ITUNES_AUTO_LINK_ALREADY_RUNNING);
        }
        itunesAutoLinkService.linkAllAsync();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(CommonResponse.success("iTunes 자동 연결을 시작했습니다."));
    }

    @GetMapping("/artists/itunes/auto-link")
    @Operation(summary = "iTunes 일괄 자동 연결 상태",
            description = "running=true면 진행 중. result는 마지막 실행 결과(서버 재시작 시 사라짐, 없으면 null). " +
                    "noCandidate = 검색 결과가 없어 직접 연결해야 하는 아티스트")
    public ResponseEntity<CommonResponse<Map<String, Object>>> itunesAutoLinkStatus() {
        Map<String, Object> body = new HashMap<>();
        body.put("running", itunesAutoLinkService.isRunning());
        body.put("result", itunesAutoLinkService.getLastResult());
        return ResponseEntity.ok(CommonResponse.success(body));
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
    @Operation(summary = "곡 CSV 다운로드", description = "UTF-8 BOM. 컬럼: id, trackId, 원문, 로마자, 한글, sortOrder, 미리듣기")
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
    @Operation(summary = "곡 CSV 업로드", description = "id(없으면 trackId)로 매칭해 로마자·한글 곡명·미리듣기 URL 반영. 빈 값은 유지, 매칭 안 된 행은 리포트. "
            + "미리듣기는 https://audio-ssl.itunes.apple.com/ 주소만(아니면 업로드 전체 400). "
            + "deleteMissing=true면 CSV에 없는 곡을 삭제(소프트 삭제, 재불러오기에도 다시 생기지 않음)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공 (결과 리포트)"),
            @ApiResponse(responseCode = "400", description = "빈 파일 / CSV 형식 오류 / iTunes가 아닌 미리듣기 URL / deleteMissing인데 매칭된 곡 없음"),
            @ApiResponse(responseCode = "404", description = "아티스트 없음")
    })
    public ResponseEntity<CommonResponse<CsvImportResult>> importCsv(
            @Parameter(description = "아티스트 PK") @PathVariable Long artistId,
            @Parameter(description = "true면 CSV에 없는 곡 삭제") @RequestParam(defaultValue = "false") boolean deleteMissing,
            @RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new BusinessException(ErrorCode.EMPTY_FILE);
        return ResponseEntity.ok(CommonResponse.success(songService.importCsv(artistId, file.getInputStream(), deleteMissing)));
    }
}
