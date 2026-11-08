package com.loop.loop_backend.Song.Service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Song.DTO.SongFetchResult;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({SongBulkFetchService.class, SongBulkFetchServiceTest.SyncExecutor.class})
class SongBulkFetchServiceTest {

    // @EnableAsync가 켜져 있어 같은 이름의 실행기가 필요하다. 테스트에서는 호출 스레드에서 바로 돌린다.
    @TestConfiguration
    static class SyncExecutor {
        @Bean(name = "itunesAutoLinkExecutor")
        SyncTaskExecutor itunesAutoLinkExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired SongBulkFetchService service;
    @Autowired ArtistRepository artistRepository;
    @Autowired EntityManager em;
    @MockitoBean SongService songService;

    @Test
    @DisplayName("iTunes에 연결된 아티스트만 곡을 불러오고, 실패한 아티스트는 결과에 남긴 채 나머지를 계속한다")
    void fetchAllAsync_WithLinkedArtists_FetchesEachAndReportsFailures() {
        // given
        service.delayMillis = 0;
        Artist yoasobi = artistRepository.save(Artist.builder().name("YOASOBI").build());
        yoasobi.linkItunes(100L, "https://music.apple.com/jp/artist/100", "YOASOBI");
        Artist ado = artistRepository.save(Artist.builder().name("Ado").build());
        ado.linkItunes(200L, "https://music.apple.com/jp/artist/200", "Ado");
        Artist unlinked = artistRepository.save(Artist.builder().name("미연결").build());
        em.flush();
        given(songService.fetchSongs(yoasobi.getId())).willReturn(new SongFetchResult(3, 2, 0, 1));
        given(songService.fetchSongs(ado.getId())).willThrow(new BusinessException(ErrorCode.ITUNES_FETCH_FAILED));

        // when
        service.fetchAllAsync();

        // then
        SongBulkFetchService.Result result = service.getLastResult();
        assertThat(result.finishedAt()).isNotNull();
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.done()).isEqualTo(2);
        assertThat(result.created()).isEqualTo(3);
        assertThat(result.updated()).isEqualTo(2);
        assertThat(result.failures()).extracting(SongBulkFetchService.Failure::artistId).containsExactly(ado.getId());
        assertThat(service.isRunning()).isFalse();
        verify(songService, times(2)).fetchSongs(anyLong());
        verify(songService, times(0)).fetchSongs(unlinked.getId());
    }
}
