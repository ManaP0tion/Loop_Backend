package com.loop.loop_backend.Artist.domain;

import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 아티스트 관련 상품 저장 요구사항:
// - 여러 개 저장하고 입력한 순서 그대로 다시 읽힌다(노출 순서).
// - 아티스트가 지워지면 상품도 함께 지워진다.
@DataJpaTest
class ArtistProductPersistenceTest {

    @Autowired private ArtistRepository artistRepository;
    @Autowired private EntityManager em;

    private Artist savedArtistWithProducts() {
        Artist artist = artistRepository.save(Artist.builder().name("Official髭男dism")
                .autoFetchConcerts(false).category(ConcertCategory.J_POP_ARTIST).build());
        artist.replaceProducts(List.of(
                new ArtistProduct("Rejoice", "https://www.cdjapan.co.jp/aff/b", "https://www.cdjapan.co.jp/image.html?id=b"),
                new ArtistProduct("Live Blu-ray", "https://www.cdjapan.co.jp/aff/a", "https://www.cdjapan.co.jp/image.html?id=a"),
                new ArtistProduct(null, "https://www.cdjapan.co.jp/aff/c", "https://www.cdjapan.co.jp/image.html?id=c")));
        em.flush();
        em.clear();
        return artist;
    }

    private long countProducts(Long artistId) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM artist_products WHERE artist_id = :id")
                .setParameter("id", artistId).getSingleResult()).longValue();
    }

    @Test
    void 상품은_입력한_순서대로_다시_읽힌다() {
        Artist artist = savedArtistWithProducts();

        Artist found = artistRepository.findById(artist.getId()).orElseThrow();

        assertThat(found.getProducts()).extracting(ArtistProduct::getTitle)
                .containsExactly("Rejoice", "Live Blu-ray", null);
    }

    @Test
    void 순서를_바꿔_다시_저장하면_바꾼_순서로_읽힌다() {
        Artist artist = savedArtistWithProducts();
        Artist found = artistRepository.findById(artist.getId()).orElseThrow();
        List<ArtistProduct> reversed = List.of(found.getProducts().get(2), found.getProducts().get(1), found.getProducts().get(0));

        found.replaceProducts(reversed);
        em.flush();
        em.clear();

        assertThat(artistRepository.findById(artist.getId()).orElseThrow().getProducts())
                .extracting(ArtistProduct::getTitle).containsExactly(null, "Live Blu-ray", "Rejoice");
    }

    @Test
    void 아티스트를_삭제하면_상품도_함께_지워진다() {
        Artist artist = savedArtistWithProducts();

        artistRepository.deleteById(artist.getId());
        em.flush();
        em.clear();

        assertThat(countProducts(artist.getId())).isZero();
    }
}