package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ConcertRepositoryTest {

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private EntityManager entityManager;

    private Concert persistConcert(String title, LocalDate startDate, LocalDate endDate) {
        Concert concert = Concert.builder()
                .title(title)
                .category(ConcertCategory.DOMESTIC_ARTIST)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        entityManager.persist(concert);
        return concert;
    }

    @Test
    void 시작일은_지났지만_종료일이_남은_콘서트는_목록에_포함된다() {
        LocalDate today = LocalDate.now();
        persistConcert("연장 공연", today.minusDays(2), today.plusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndated(today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("연장 공연");
    }

    @Test
    void 종료일까지_지난_콘서트는_목록에서_제외된다() {
        LocalDate today = LocalDate.now();
        persistConcert("종료된 공연", today.minusDays(5), today.minusDays(1));
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndated(today);

        assertThat(result).isEmpty();
    }

    @Test
    void 종료일이_없으면_시작일을_기준으로_판단한다() {
        LocalDate today = LocalDate.now();
        persistConcert("종료일_없음_미래", today.plusDays(1), null);
        persistConcert("종료일_없음_과거", today.minusDays(1), null);
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndated(today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("종료일_없음_미래");
    }

    @Test
    void 시작일_종료일_모두_없으면_날짜_미정으로_항상_포함된다() {
        LocalDate today = LocalDate.now();
        persistConcert("날짜_미정", null, null);
        entityManager.flush();

        List<Concert> result = concertRepository.findUpcomingOrUndated(today);

        assertThat(result).extracting(Concert::getTitle).containsExactly("날짜_미정");
    }
}