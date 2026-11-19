package com.loop.loop_backend.Artist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.infra.itunes.ItunesClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 아티스트 삭제 요구사항:
// - 곡이 등록된 아티스트도 삭제할 수 있다. 아티스트를 지우면 그 아티스트의 곡(관리자가 지운 곡 포함)도 함께 지워진다.
// - 다른 아티스트의 곡은 남는다.
// - 없는 아티스트를 지우면 ARTIST_NOT_FOUND.
@DataJpaTest
@Import(ArtistServiceImpl.class)
class ArtistDeleteTest {

    @Autowired private ArtistService artistService;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private EntityManager em;
    @MockitoBean private ItunesClient itunesClient;

    private Artist artist(String name) {
        return artistRepository.save(Artist.builder().name(name).build());
    }

    private Song song(Artist artist, String title) {
        Song song = Song.builder().artist(artist).titleOriginal(title).sortOrder(1).build();
        em.persist(song);
        return song;
    }

    // @SQLRestriction을 우회해 지운 곡까지 센다
    private long songCount(Long artistId) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM song WHERE artist_id = :id")
                .setParameter("id", artistId).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("곡이 있는 아티스트를 삭제하면 아티스트와 그 곡이 모두 지워진다")
    void deleteArtist_WithSongs_DeletesArtistAndSongs() {
        // given
        Artist artist = artist("YOASOBI");
        song(artist, "夜に駆ける");
        song(artist, "アイドル");
        em.flush();
        em.clear();

        // when
        artistService.deleteArtist(artist.getId());
        em.flush();
        em.clear();

        // then
        assertThat(artistRepository.findById(artist.getId())).isEmpty();
        assertThat(songCount(artist.getId())).isZero();
    }

    @Test
    @DisplayName("관리자가 지운 곡만 남은 아티스트도 삭제된다")
    void deleteArtist_WithSoftDeletedSong_DeletesArtist() {
        // given
        Artist artist = artist("Vaundy");
        Song song = song(artist, "怪獣の花唄");
        em.flush();
        em.remove(song);
        em.flush();
        em.clear();

        // when
        artistService.deleteArtist(artist.getId());
        em.flush();
        em.clear();

        // then
        assertThat(artistRepository.findById(artist.getId())).isEmpty();
        assertThat(songCount(artist.getId())).isZero();
    }

    @Test
    @DisplayName("아티스트를 삭제해도 다른 아티스트의 곡은 남는다")
    void deleteArtist_KeepsOtherArtistSongs() {
        // given
        Artist target = artist("YOASOBI");
        Artist other = artist("Ado");
        song(target, "夜に駆ける");
        song(other, "うっせぇわ");
        em.flush();
        em.clear();

        // when
        artistService.deleteArtist(target.getId());
        em.flush();
        em.clear();

        // then
        assertThat(artistRepository.findById(other.getId())).isPresent();
        assertThat(songCount(other.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 아티스트를 삭제하면 ARTIST_NOT_FOUND")
    void deleteArtist_NotFound() {
        assertThatThrownBy(() -> artistService.deleteArtist(999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ARTIST_NOT_FOUND);
    }
}