package com.loop.loop_backend.Artist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.infra.itunes.ItunesArtist;
import com.loop.loop_backend.infra.itunes.ItunesClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({ItunesAutoLinkService.class, ItunesAutoLinkServiceTest.SyncExecutor.class})
class ItunesAutoLinkServiceTest {

    // @EnableAsync가 켜져 있어 같은 이름의 실행기가 필요하다. 테스트에서는 호출 스레드에서 바로 돌린다.
    @TestConfiguration
    static class SyncExecutor {
        @Bean(name = "itunesAutoLinkExecutor")
        SyncTaskExecutor itunesAutoLinkExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired ItunesAutoLinkService service;
    @Autowired ArtistRepository artistRepository;
    @Autowired EntityManager em;
    @MockitoBean ItunesClient itunesClient;

    @Test
    @DisplayName("미연결 아티스트만 1순위 후보에 자동 연결하고, 이름이 같으면 확인 없이 확정하며, 후보 없음·검색 실패는 결과에 남긴다")
    void linkAllAsync_WithUnlinkedArtists_LinksTopCandidateAndReportsRest() {
        // given
        service.delayMillis = 0;
        Artist yoasobi = artistRepository.save(Artist.builder().name("YOASOBI").build());
        Artist unknown = artistRepository.save(Artist.builder().name("없는가수").build());
        Artist broken = artistRepository.save(Artist.builder().name("Ado").build());
        Artist yorushika = artistRepository.save(Artist.builder().name("요루시카").build());
        Artist manual = artistRepository.save(Artist.builder().name("King Gnu").build());
        manual.linkItunes(999L, "https://music.apple.com/jp/artist/999", "King Gnu");
        em.flush();
        given(itunesClient.searchArtists("YOASOBI")).willReturn(List.of(
                new ItunesArtist("artist", 100L, "YOASOBI", "https://music.apple.com/jp/artist/100", "J-Pop"),
                new ItunesArtist("artist", 200L, "YOASOBI Tribute", null, null)));
        given(itunesClient.searchArtists("요루시카")).willReturn(List.of(
                new ItunesArtist("artist", 300L, "ヨルシカ", "https://music.apple.com/jp/artist/300", "J-Pop")));
        given(itunesClient.searchArtists("없는가수")).willReturn(List.of());
        given(itunesClient.searchArtists("Ado")).willThrow(new BusinessException(ErrorCode.ITUNES_FETCH_FAILED));

        // when
        service.linkAllAsync();

        // then
        ItunesAutoLinkService.Result result = service.getLastResult();
        assertThat(result.finishedAt()).isNotNull();
        assertThat(result.linked()).isEqualTo(2);
        assertThat(result.noCandidate()).extracting(ItunesAutoLinkService.Failure::artistId).containsExactly(unknown.getId());
        assertThat(result.failures()).extracting(ItunesAutoLinkService.Failure::artistId).containsExactly(broken.getId());
        verify(itunesClient, never()).searchArtists("King Gnu");

        em.clear();
        Artist linked = artistRepository.findById(yoasobi.getId()).orElseThrow();
        assertThat(linked.getItunesArtistId()).isEqualTo(100L);
        assertThat(linked.getItunesArtistName()).isEqualTo("YOASOBI");
        assertThat(linked.isItunesAutoLinked()).isFalse(); // 이름이 같아 확정
        assertThat(artistRepository.findById(manual.getId()).orElseThrow().isItunesAutoLinked()).isFalse();

        // 이름이 다르면 확인 대기, 관리자 확인(같은 ID로 다시 연결) → 자동 연결 표시 해제
        Artist pending = artistRepository.findById(yorushika.getId()).orElseThrow();
        assertThat(pending.getItunesArtistId()).isEqualTo(300L);
        assertThat(pending.isItunesAutoLinked()).isTrue();
        pending.linkItunes(300L, pending.getArtistViewUrl(), "ヨルシカ");
        assertThat(pending.isItunesAutoLinked()).isFalse();
    }
}
