package com.loop.loop_backend.Song.Service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Song.DTO.*;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.infra.itunes.ItunesClient;
import com.loop.loop_backend.infra.itunes.ItunesTrack;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SongService {

    static final String[] CSV_HEADER = {"trackId", "원문", "로마자", "한글", "sortOrder"};
    private static final char BOM = '﻿';

    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final ItunesClient itunesClient;
    private final S3StorageService s3StorageService;

    public List<SongResponse> getSongs(Long artistId) {
        findArtistOrThrow(artistId);
        return songRepository.findAllByArtistIdOrderBySortOrderAsc(artistId)
                .stream().map(SongResponse::from).toList();
    }

    @Transactional
    public SongResponse createSong(Long artistId, SongCreateRequest req) {
        Artist artist = findArtistOrThrow(artistId);
        Song song = Song.builder()
                .artist(artist)
                .titleOriginal(req.titleOriginal())
                .titleRomanized(req.titleRomanized())
                .titleKo(req.titleKo())
                .albumArtUrl(req.albumArtUrl())
                .sortOrder(songRepository.findMaxSortOrder(artistId) + 1)
                .build();
        return SongResponse.from(songRepository.save(song));
    }

    @Transactional
    public SongResponse updateSong(Long songId, SongUpdateRequest req) {
        Song song = findSongOrThrow(songId);
        song.update(req.titleOriginal(), req.titleRomanized(), req.titleKo(), req.albumArtUrl());
        return SongResponse.from(song);
    }

    // S3 공개 버킷 업로드 후 albumArtUrl 갱신. iTunes 곡은 재불러오기 시 iTunes 앨범아트로 덮어써진다
    @Transactional
    public SongResponse uploadAlbumArt(Long songId, MultipartFile image) {
        Song song = findSongOrThrow(songId);
        String url = s3StorageService.uploadPublic("songs", songId, image);
        song.update(song.getTitleOriginal(), song.getTitleRomanized(), song.getTitleKo(), url);
        return SongResponse.from(song);
    }

    @Transactional
    public void deleteSong(Long songId) {
        songRepository.delete(findSongOrThrow(songId));
    }

    /**
     * iTunes(JP)에서 곡을 불러와 trackId 기준 upsert.
     * JP 스토어 응답 순서 = sortOrder, US 스토어 곡명 = 로마자(영문 번역 제목이 섞임, 참고값).
     * 기존 곡의 titleKo는 유지, 로마자는 비어 있을 때만 채움, 소프트 삭제 곡은 skip.
     * iTunes는 같은 곡을 싱글/앨범/라이브마다 다른 trackId로 주므로, 원문 제목이 완전히 같으면 처음 나온 것만 저장.
     */
    @Transactional
    public SongFetchResult fetchSongs(Long artistId) {
        Artist artist = findArtistOrThrow(artistId);
        if (artist.getItunesArtistId() == null) throw new BusinessException(ErrorCode.ARTIST_NOT_LINKED);

        List<ItunesTrack> jaTracks = itunesClient.fetchTracks(artist.getItunesArtistId(), ItunesClient.COUNTRY_JP);
        Map<Long, String> romanized = itunesClient.fetchTracks(artist.getItunesArtistId(), ItunesClient.COUNTRY_US)
                .stream()
                .collect(Collectors.toMap(ItunesTrack::trackId, ItunesTrack::trackName, (a, b) -> a));

        List<Song> activeSongs = songRepository.findAllByArtistIdOrderBySortOrderAsc(artistId);
        Map<Long, Song> existing = activeSongs.stream().filter(s -> s.getTrackId() != null)
                .collect(Collectors.toMap(Song::getTrackId, Function.identity()));
        Map<String, Song> existingByTitle = activeSongs.stream()
                .collect(Collectors.toMap(Song::getTitleOriginal, Function.identity(), (a, b) -> a));
        Set<Long> deleted = new HashSet<>(songRepository.findDeletedTrackIds(artistId));

        int created = 0, updated = 0, skipped = 0, duplicated = 0, order = 0;
        Set<String> seenTitles = new HashSet<>();
        for (ItunesTrack t : jaTracks) {
            if (t.trackId() == null || t.trackName() == null) continue;
            if (!seenTitles.add(t.trackName())) { // 같은 제목의 다른 버전
                duplicated++;
                continue;
            }
            order++;
            if (deleted.contains(t.trackId())) {
                skipped++;
                continue;
            }
            String art = toAlbumArt200(t.artworkUrl100());
            // US 스토어에도 원문 그대로인 곡(TAIDADA 등)은 로마자 없음으로 취급
            String rom = romanized.get(t.trackId());
            if (t.trackName().equals(rom)) rom = null;
            // trackId가 달라도 같은 제목 곡이 이미 있으면 그 곡을 갱신 (대표 버전이 바뀌어도 중복 생성 안 함)
            Song song = existing.getOrDefault(t.trackId(), existingByTitle.get(t.trackName()));
            if (song != null) {
                song.updateFromItunes(t.trackName(), rom, art, order);
                updated++;
            } else {
                songRepository.save(Song.builder()
                        .artist(artist)
                        .trackId(t.trackId())
                        .titleOriginal(t.trackName())
                        .titleRomanized(rom)
                        .albumArtUrl(art)
                        .sortOrder(order)
                        .build());
                created++;
            }
        }
        return new SongFetchResult(created, updated, skipped, duplicated);
    }

    /** CSV 다운로드 (UTF-8 BOM 포함, 엑셀 한글 깨짐 방지) */
    public byte[] exportCsv(Long artistId) {
        findArtistOrThrow(artistId);
        StringWriter out = new StringWriter();
        out.write(BOM);
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(CSV_HEADER).build())) {
            for (Song s : songRepository.findAllByArtistIdOrderBySortOrderAsc(artistId)) {
                printer.printRecord(s.getTrackId(), s.getTitleOriginal(), s.getTitleRomanized(),
                        s.getTitleKo(), s.getSortOrder());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * CSV 업로드: trackId로 매칭해 로마자·한글만 반영. 빈 값은 기존 값 유지, 매칭 안 된 행은 리포트로 반환.
     * 로마자는 US 스토어에 없는 곡·영문 번역 제목 보정용 (재불러오기는 로마자가 비어 있을 때만 채움)
     */
    @Transactional
    public CsvImportResult importCsv(Long artistId, InputStream in) {
        findArtistOrThrow(artistId);
        Map<Long, Song> songs = activeSongsByTrackId(artistId);
        int updated = 0, unchanged = 0;
        List<Long> unmatched = new ArrayList<>();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).build();
        try (Reader reader = skipBom(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (CSVRecord r : format.parse(reader)) {
                Song song = songs.get(parseLong(r.isMapped("trackId") ? r.get("trackId") : null));
                if (song == null) {
                    unmatched.add(r.getRecordNumber());
                    continue;
                }
                String romanized = column(r, "로마자");
                String ko = column(r, "한글");
                if (romanized.isBlank() && ko.isBlank()) {
                    unchanged++;
                } else {
                    song.updateFromCsv(romanized, ko);
                    updated++;
                }
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            // 깨진 CSV(따옴표 짝 안 맞음, 헤더 중복 등)
            throw new BusinessException(ErrorCode.INVALID_REQUEST_FORMAT);
        }
        return new CsvImportResult(updated, unchanged, unmatched);
    }

    static String toAlbumArt200(String url) {
        if (url == null) return null;
        return url.replace("100x100", "200x200").replaceFirst("^http://", "https://");
    }

    private static String column(CSVRecord r, String name) {
        return r.isMapped(name) && r.isSet(name) ? r.get(name) : "";
    }

    private static Long parseLong(String v) {
        try {
            return v == null || v.isBlank() ? null : Long.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Reader skipBom(Reader reader) throws IOException {
        PushbackReader r = new PushbackReader(reader);
        int c = r.read();
        if (c != BOM && c != -1) r.unread(c);
        return r;
    }

    private Map<Long, Song> activeSongsByTrackId(Long artistId) {
        return songRepository.findAllByArtistIdOrderBySortOrderAsc(artistId).stream()
                .filter(s -> s.getTrackId() != null)
                .collect(Collectors.toMap(Song::getTrackId, Function.identity()));
    }

    private Artist findArtistOrThrow(Long artistId) {
        return artistRepository.findById(artistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
    }

    private Song findSongOrThrow(Long songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SONG_NOT_FOUND));
    }
}
