package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 예매처 목록(JSON 변환 컬럼)과 공연장 필드가 DB에 저장됐다가 그대로 읽히는지 검증한다.
@DataJpaTest
class ConcertDetailPersistenceTest {

    @Autowired
    private EntityManager entityManager;

    private Concert saveAndReload(Concert concert) {
        entityManager.persist(concert);
        entityManager.flush();
        entityManager.clear(); // 1차 캐시를 비워서 DB에서 실제로 다시 읽게 한다
        return entityManager.find(Concert.class, concert.getId());
    }

    @Test
    void 예매처_여러_곳과_공연장_정보가_저장된_그대로_읽힌다() {
        List<TicketVendorInfo> vendors = List.of(
                new TicketVendorInfo("인터파크", "http://ticket.example/goods?code=1&x=한글"),
                new TicketVendorInfo("멜론티켓", "http://b.example/2"));

        Concert loaded = saveAndReload(Concert.builder()
                .title("Vaundy ASIA ARENA TOUR")
                .category(ConcertCategory.J_POP_ARTIST)
                .ticketVendors(vendors)
                .venueAddress("인천광역시 중구 공항문화로 127 (운서동)")
                .venueLatitude(37.4655301)
                .venueLongitude(126.3891177)
                .venueCapacity(14483)
                .build());

        assertThat(loaded.getTicketVendors()).containsExactlyElementsOf(vendors);
        assertThat(loaded.getVenueAddress()).isEqualTo("인천광역시 중구 공항문화로 127 (운서동)");
        assertThat(loaded.getVenueLatitude()).isEqualTo(37.4655301);
        assertThat(loaded.getVenueLongitude()).isEqualTo(126.3891177);
        assertThat(loaded.getVenueCapacity()).isEqualTo(14483);
    }

    @Test
    void 새_필드가_없는_기존_콘서트는_그대로_null로_읽힌다() {
        Concert loaded = saveAndReload(Concert.builder()
                .title("기존 콘서트")
                .category(ConcertCategory.DOMESTIC_ARTIST)
                .build());

        assertThat(loaded.getTicketVendors()).isNull();
        assertThat(loaded.getVenueAddress()).isNull();
        assertThat(loaded.getVenueLatitude()).isNull();
        assertThat(loaded.getVenueCapacity()).isNull();
    }

    @Test
    void 예매처가_빈_목록이면_null이_아니라_빈_목록으로_읽힌다() {
        Concert loaded = saveAndReload(Concert.builder()
                .title("예매처 없는 콘서트")
                .category(ConcertCategory.DOMESTIC_ARTIST)
                .ticketVendors(List.of())
                .build());

        assertThat(loaded.getTicketVendors()).isEmpty();
    }
}