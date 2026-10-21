package com.loop.loop_backend.Venue.domain;

import com.loop.loop_backend.Venue.repository.VenueRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

// 숙소 저장 요구사항(공연장 단위):
// - 숙소 항목이 생기기 전부터 있던 공연장은 숙소 노출 Off로 남는다(사용자 화면에 갑자기 숙소가 나오지 않게).
// - 저장한 노출 여부와 딥링크(1000자까지)는 다시 읽어도 그대로다.
@DataJpaTest
class VenueLodgingPersistenceTest {

    @Autowired private VenueRepository venueRepository;
    @Autowired private EntityManager em;

    @Test
    @DisplayName("숙소 항목이 생기기 전부터 있던 공연장은 숙소 노출이 꺼진 상태로 읽힌다")
    void existingVenueRow_WithoutLodgingColumns_IsLodgingOff() {
        // given - 컬럼이 추가될 때 기존 행은 컬럼 기본값으로 채워진다: 숙소 값을 지정하지 않은 행
        em.createNativeQuery("INSERT INTO venues (name, address, created_at, updated_at) "
                + "VALUES ('기존 공연장', '서울특별시 송파구', NOW(), NOW())").executeUpdate();

        // when
        Object lodgingVisible = em.createNativeQuery(
                "SELECT lodging_visible FROM venues WHERE name = '기존 공연장'").getSingleResult();

        // then
        assertThat(lodgingVisible).isEqualTo(false);
    }

    @Test
    @DisplayName("저장한 숙소 노출 여부와 1000자 딥링크는 다시 읽어도 그대로다")
    void saveVenue_WithLodging_PersistsVisibleAndUrl() {
        // given
        String prefix = "https://trip.example/?q=";
        String longDeeplink = prefix + "a".repeat(1000 - prefix.length());
        Venue venue = Venue.builder().name("KSPO DOME").address("서울특별시 송파구").build();
        venue.changeLodging(true, longDeeplink);
        Long id = venueRepository.save(venue).getId();
        em.flush();
        em.clear();

        // when
        Venue found = venueRepository.findById(id).orElseThrow();

        // then
        assertThat(found.isLodgingVisible()).isTrue();
        assertThat(found.getLodgingUrl()).isEqualTo(longDeeplink);
    }
}
