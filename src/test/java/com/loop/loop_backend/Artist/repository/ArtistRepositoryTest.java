package com.loop.loop_backend.Artist.repository;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.domain.Nationality;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

// 아티스트 국적 요구사항:
// - 국적은 JP(일본) / KR(국내). 입력하지 않으면 국적 없이(null) 저장된다.
// - KOPIS 직접 등록 추천 대상은 국내(KR)를 뺀 아티스트 전체다 - 국적을 아직 입력하지 않은 아티스트도 포함한다.
@DataJpaTest
class ArtistRepositoryTest {

    @Autowired private ArtistRepository artistRepository;
    @Autowired private EntityManager em;

    private Artist artist(String name, Nationality nationality) {
        return artistRepository.save(Artist.builder().name(name).nationality(nationality).build());
    }

    @Test
    @DisplayName("국적을 입력하지 않으면 국적 없이(null) 저장된다")
    void save_WithoutNationality_StoresNull() {
        // given
        Long id = artistRepository.save(Artist.builder().name("Vaundy").build()).getId();
        em.flush();
        em.clear();

        // when
        Artist found = artistRepository.findById(id).orElseThrow();

        // then
        assertThat(found.getNationality()).isNull();
    }

    @Test
    @DisplayName("국내(KR)를 빼고 조회하면 일본과 국적 미입력 아티스트가 나온다")
    void findByNationalityNotOrNationalityIsNull_ExcludesOnlyDomestic() {
        // given
        artist("YOASOBI", Nationality.JP);
        artist("Vaundy", null);
        artist("잔나비", Nationality.KR);
        em.flush();
        em.clear();

        // when & then
        assertThat(artistRepository.findByNationalityNotOrNationalityIsNull(Nationality.KR))
                .extracting(Artist::getName)
                .containsExactlyInAnyOrder("YOASOBI", "Vaundy");
    }
}
