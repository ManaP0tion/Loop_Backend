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
import org.junit.jupiter.api.DisplayName;
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
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), false);
        assertThat(imported).isEqualTo(new CsvImportResult(2, 1, 0, List.of(4L)));
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
                .startsWith("﻿id,trackId,원문,로마자,한글,sortOrder");

        // 수동 추가: sortOrder = max + 1 (아이돌 = 3)
        assertThat(songService.createSong(artist.getId(),
                new com.loop.loop_backend.Song.DTO.SongCreateRequest("新曲", null, null, null)).sortOrder())
                .isEqualTo(4);
    }

    private ItunesTrack track(Long id, String name, String art) {
        return new ItunesTrack("track", id, name, null, art, null);
    }

    @Test
    @DisplayName("iTunes 불러오기는 미리듣기 URL을 저장하고, 재불러오기 때 새 URL로 갱신한다")
    void fetchSongs_WithPreviewUrl_SavesAndRefreshesPreviewUrl() {
        // given
        Artist artist = artistRepository.save(Artist.builder().name("YOASOBI").build());
        artist.linkItunes(100L, "https://music.apple.com/jp/artist/100");
        given(itunesClient.fetchTracks(eq(100L), eq(ItunesClient.COUNTRY_JP))).willReturn(List.of(
                new ItunesTrack("track", 1L, "夜に駆ける", null, null, "https://audio/old.m4a")));
        songService.fetchSongs(artist.getId());

        // when
        given(itunesClient.fetchTracks(eq(100L), eq(ItunesClient.COUNTRY_JP))).willReturn(List.of(
                new ItunesTrack("track", 1L, "夜に駆ける", null, null, "https://audio/new.m4a")));
        songService.fetchSongs(artist.getId());
        em.flush();
        em.clear();

        // then
        assertThat(em.createQuery("select s.previewUrl from Song s", String.class).getResultList())
                .containsExactly("https://audio/new.m4a");
    }

    @Test
    void 재불러오기시_수동추가곡과_sortOrder가_겹치지_않는다() {
        Artist artist = artistRepository.save(Artist.builder().name("YOASOBI").build());
        artist.linkItunes(100L, "https://music.apple.com/jp/artist/100");
        given(itunesClient.fetchTracks(eq(100L), eq(ItunesClient.COUNTRY_JP))).willReturn(List.of(
                track(1L, "夜に駆ける", null), track(2L, "群青", null)));
        songService.fetchSongs(artist.getId());
        // 수동 추가 → max + 1 = 3
        songService.createSong(artist.getId(), new com.loop.loop_backend.Song.DTO.SongCreateRequest("新曲", null, null, null));

        // iTunes에 신곡이 생겨 3번째로 내려옴 → 예전엔 이 곡도 sortOrder 3을 받아 수동곡과 중복
        given(itunesClient.fetchTracks(eq(100L), eq(ItunesClient.COUNTRY_JP))).willReturn(List.of(
                track(1L, "夜に駆ける", null), track(2L, "群青", null), track(5L, "アイドル", null)));
        songService.fetchSongs(artist.getId());
        em.flush();
        em.clear();

        List<SongResponse> songs = songService.getSongs(artist.getId());
        assertThat(songs).extracting(SongResponse::sortOrder).containsExactly(1, 2, 3, 4);
        assertThat(songs).extracting(SongResponse::titleOriginal).containsExactly("夜に駆ける", "群青", "アイドル", "新曲");
    }

    @Test
    @DisplayName("CSV 미리듣기 컬럼으로 수동 추가 곡에 URL을 넣고, iTunes에 미리듣기가 없는 곡은 재불러오기에도 CSV 값을 유지한다")
    void importCsv_WithPreviewUrl_SetsUrlAndKeepsItWhenItunesHasNone() {
        // given: iTunes 곡(미리듣기 없음) + 수동 곡
        Artist artist = artistRepository.save(Artist.builder().name("YOASOBI").build());
        artist.linkItunes(100L, "https://music.apple.com/jp/artist/100");
        given(itunesClient.fetchTracks(eq(100L), eq(ItunesClient.COUNTRY_JP))).willReturn(List.of(track(1L, "夜に駆ける", null)));
        songService.fetchSongs(artist.getId());
        Long manual = songService.createSong(artist.getId(),
                new com.loop.loop_backend.Song.DTO.SongCreateRequest("新曲", null, null, null)).id();
        String url1 = "https://audio-ssl.itunes.apple.com/a.m4a";
        String url2 = "https://audio-ssl.itunes.apple.com/b.m4a";
        String csv = "id,trackId,원문,로마자,한글,sortOrder,미리듣기\n"
                + ",1,夜に駆ける,,,1," + url1 + "\n"
                + manual + ",,新曲,,,2," + url2 + "\n";

        // when
        CsvImportResult r = songService.importCsv(artist.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), false);
        songService.fetchSongs(artist.getId());
        em.flush();
        em.clear();

        // then
        assertThat(r.updated()).isEqualTo(2);
        assertThat(em.createQuery("select s.previewUrl from Song s order by s.sortOrder", String.class).getResultList())
                .containsExactly(url1, url2);
        assertThat(new String(songService.exportCsv(artist.getId()), StandardCharsets.UTF_8)).contains(url1, url2);
    }

    @Test
    @DisplayName("CSV 미리듣기에 iTunes 미리듣기 주소가 아닌 URL을 넣으면 업로드 전체가 400이다")
    void importCsv_WithNonItunesPreviewUrl_ThrowsBadRequest() {
        // given
        Artist artist = artistRepository.save(Artist.builder().name("YOASOBI").build());
        Long id = songService.createSong(artist.getId(),
                new com.loop.loop_backend.Song.DTO.SongCreateRequest("新曲", null, null, null)).id();
        String csv = "id,trackId,원문,로마자,한글,sortOrder,미리듣기\n" + id + ",,新曲,,,1,https://youtu.be/abc\n";

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> songService.importCsv(artist.getId(),
                        new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), false))
                .isInstanceOf(com.loop.loop_backend.common.exception.BusinessException.class)
                .hasMessageContaining("미리듣기");
    }

    @Test
    void CSV_deleteMissing이면_CSV에_없는_곡을_삭제하고_수동곡은_id로_매칭한다() {
        Artist artist = artistRepository.save(Artist.builder().name("Omoinotake").build());
        Long keepManual = songService.createSong(artist.getId(),
                new com.loop.loop_backend.Song.DTO.SongCreateRequest("幾億光年", null, null, null)).id();
        songService.createSong(artist.getId(), new com.loop.loop_backend.Song.DTO.SongCreateRequest("幾億光年 (Instrumental)", null, null, null));
        songService.createSong(artist.getId(), new com.loop.loop_backend.Song.DTO.SongCreateRequest("幾億光年 (Live)", null, null, null));

        String csv = "id,trackId,원문,로마자,한글,sortOrder\n" + keepManual + ",,幾億光年,,수억 광년,1\n";
        CsvImportResult r = songService.importCsv(artist.getId(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), true);

        assertThat(r).isEqualTo(new CsvImportResult(1, 0, 2, List.of()));
        em.flush();
        em.clear();
        assertThat(songService.getSongs(artist.getId())).extracting(SongResponse::titleKo).containsExactly("수억 광년");
    }

    @Test
    void CSV_deleteMissing인데_한_곡도_매칭되지_않으면_아무것도_지우지_않는다() {
        Artist artist = artistRepository.save(Artist.builder().name("Omoinotake").build());
        songService.createSong(artist.getId(), new com.loop.loop_backend.Song.DTO.SongCreateRequest("幾億光年", null, null, null));

        String otherArtistCsv = "id,trackId,원문,로마자,한글,sortOrder\n99999,,남의 곡,,,1\n";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> songService.importCsv(artist.getId(),
                        new ByteArrayInputStream(otherArtistCsv.getBytes(StandardCharsets.UTF_8)), true))
                .isInstanceOf(com.loop.loop_backend.common.exception.BusinessException.class);
        assertThat(songService.getSongs(artist.getId())).hasSize(1);
    }
}
