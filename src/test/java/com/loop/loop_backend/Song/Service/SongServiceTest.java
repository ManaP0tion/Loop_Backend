package com.loop.loop_backend.Song.Service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Song.DTO.CsvImportResult;
import com.loop.loop_backend.Song.DTO.SongFetchResult;
import com.loop.loop_backend.Song.DTO.SongResponse;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.infra.itunes.ItunesClient;
import com.loop.loop_backend.infra.itunes.ItunesTrack;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@DataJpaTest
@Import(SongService.class)
class SongServiceTest {

    @Autowired SongService songService;
    @Autowired SongRepository songRepository;
    @Autowired ArtistRepository artistRepository;
    @Autowired EntityManager em;
    @MockitoBean ItunesClient itunesClient;
    @MockitoBean S3StorageService s3StorageService;

    @Test
    void 불러오기_병합_재불러오기시_한글유지_삭제곡skip_CSV반영() {
        Artist artist = artistRepository.save(Artist.builder().name("YOASOBI").build());
        artist.linkItunes(100L, "https://music.apple.com/jp/artist/100");
        given(itunesClient.fetchTracks(eq(100L), eq(ItunesClient.COUNTRY_JP))).willReturn(List.of(
                track(1L, "夜に駆ける", "http://x/100x100bb.jpg"),
                track(2L, "群青", null),
                track(3L, "アイドル", null),
                track(4L, "夜に駆ける", null))); // 같은 제목의 다른 버전 → 중복
        given(itunesClient.fetchTracks(eq(100L), eq(ItunesClient.COUNTRY_US))).willReturn(List.of(
                track(3L, "アイドル", null), // US 스토어도 원문 그대로 → 로마자 없음
                track(1L, "Yoru ni Kakeru", null)));

        // 첫 불러오기: JP 순서 = sortOrder, US 곡명 = 로마자 (US에 없는 곡은 null), 앨범아트 200x200 + https
        assertThat(songService.fetchSongs(artist.getId())).isEqualTo(new SongFetchResult(3, 0, 0, 1));
        List<SongResponse> songs = songService.getSongs(artist.getId());
        assertThat(songs).extracting(SongResponse::titleOriginal).containsExactly("夜に駆ける", "群青", "アイドル");
        assertThat(songs.get(0).titleRomanized()).isEqualTo("Yoru ni Kakeru");
        assertThat(songs.get(1).titleRomanized()).isNull();
        assertThat(songs.get(2).titleRomanized()).isNull();
        assertThat(songs.get(0).albumArtUrl()).isEqualTo("https://x/200x200bb.jpg");

        // CSV 업로드: BOM 포함, 로마자·한글 반영, 빈 칸은 유지, 없는 trackId는 리포트
        String csv = "﻿trackId,원문,로마자,한글,sortOrder\n"
                + "1,夜に駆ける,,밤을 달리다,1\n2,群青,,,2\n3,アイドル,Idol,,3\n999,없음,,무시,4\n";
        CsvImportResult imported = songService.importCsv(artist.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertThat(imported).isEqualTo(new CsvImportResult(2, 1, List.of(4L)));
        List<SongResponse> afterCsv = songService.getSongs(artist.getId());
        assertThat(afterCsv.get(0).titleRomanized()).isEqualTo("Yoru ni Kakeru"); // 빈 칸 → 유지
        assertThat(afterCsv.get(1).titleKo()).isNull();
        assertThat(afterCsv.get(2).titleRomanized()).isEqualTo("Idol");

        // 2번 곡 소프트 삭제 후 재불러오기: 삭제곡 skip, titleKo 유지, 로마자는 비어 있을 때만 채움
        songService.deleteSong(songs.get(1).id());
        given(itunesClient.fetchTracks(eq(100L), eq(ItunesClient.COUNTRY_US))).willReturn(List.of(
                track(3L, "Idol (English)", null), // 이미 수동 입력값이 있으면 덮어쓰지 않음
                track(1L, "Yoru ni Kakeru", null)));
        em.flush();
        em.clear();
        assertThat(songService.fetchSongs(artist.getId())).isEqualTo(new SongFetchResult(0, 2, 1, 1));
        em.flush();
        em.clear();
        List<SongResponse> after = songService.getSongs(artist.getId());
        assertThat(after).extracting(SongResponse::trackId).containsExactly(1L, 3L);
        assertThat(after.get(0).titleKo()).isEqualTo("밤을 달리다");
        assertThat(after.get(1).titleRomanized()).isEqualTo("Idol"); // 수동 로마자 보존

        // CSV 다운로드는 BOM으로 시작
        assertThat(new String(songService.exportCsv(artist.getId()), StandardCharsets.UTF_8))
                .startsWith("﻿trackId,원문,로마자,한글,sortOrder");

        // 수동 추가: sortOrder = max + 1 (아이돌 = 3)
        assertThat(songService.createSong(artist.getId(),
                new com.loop.loop_backend.Song.DTO.SongCreateRequest("新曲", null, null, null)).sortOrder())
                .isEqualTo(4);
    }

    private ItunesTrack track(Long id, String name, String art) {
        return new ItunesTrack("track", id, name, null, art);
    }
}
