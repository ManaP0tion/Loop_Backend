package com.loop.loop_backend.Concert.domain;

import com.loop.loop_backend.Concert.repository.ConcertRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 공연별 동행 기능 상태 요구사항:
// - 상태는 미오픈 / 모집 중 / 종료 세 가지. 미오픈(연 적 없음)과 종료(열었다 닫음)는 화면이 달라 구분한다.
// - 새 공연은 페스티벌이면 모집 중, 단독 공연이면 미오픈으로 시작한다. 공연 유형을 바꿔도 상태는 유지된다.
// - 관리자가 ON하면 모집 중, 모집 중을 OFF하면 종료. 미오픈을 OFF해도 미오픈 그대로(연 적 없는 공연이 '종료'로 보이면 안 된다).
// - 종료한 공연을 다시 ON하면 모집 중으로 돌아간다.
// - 비공개 공연은 저장된 상태와 상관없이 OFF처럼 보인다(열린 적이 있으면 종료, 없으면 미오픈). 다시 공개하면 바로 원래대로 보인다.
@DataJpaTest
class ConcertCompanionStatusTest {

    @Autowired private ConcertRepository concertRepository;
    @Autowired private EntityManager em;

    private Concert save(ConcertCategory category, boolean published) {
        Concert concert = concertRepository.save(Concert.builder()
                .title("공연")
                .category(category)
                .published(published)
                .build());
        em.flush();
        em.clear();
        return concertRepository.findById(concert.getId()).orElseThrow();
    }

    private Concert reload(Concert concert) {
        em.flush();
        em.clear();
        return concertRepository.findById(concert.getId()).orElseThrow();
    }

    @Test
    @DisplayName("새 페스티벌은 모집 중, 새 단독 공연은 미오픈으로 저장된다")
    void newConcert_DefaultsByCategory() {
        assertThat(save(ConcertCategory.FESTIVAL, true).getCompanionStatus()).isEqualTo(CompanionStatus.OPEN);
        assertThat(save(ConcertCategory.STAGE, true).getCompanionStatus()).isEqualTo(CompanionStatus.NOT_OPENED);
    }

    @Test
    @DisplayName("공연 유형을 바꿔도 동행 상태는 유지된다")
    void changeCategory_KeepsStatus() {
        // given
        Concert stage = save(ConcertCategory.STAGE, true);

        // when
        stage.changeCategory(ConcertCategory.FESTIVAL);

        // then
        assertThat(reload(stage).getCompanionStatus()).isEqualTo(CompanionStatus.NOT_OPENED);
    }

    @Test
    @DisplayName("미오픈을 켜면 모집 중, 모집 중을 끄면 종료, 종료를 다시 켜면 모집 중이다")
    void toggle_OpenCloseReopen() {
        Concert concert = save(ConcertCategory.STAGE, true);

        concert.changeCompanionEnabled(true);
        concert = reload(concert);
        assertThat(concert.getCompanionStatus()).isEqualTo(CompanionStatus.OPEN);

        concert.changeCompanionEnabled(false);
        concert = reload(concert);
        assertThat(concert.getCompanionStatus()).isEqualTo(CompanionStatus.CLOSED);

        concert.changeCompanionEnabled(true);
        assertThat(reload(concert).getCompanionStatus()).isEqualTo(CompanionStatus.OPEN);
    }

    @Test
    @DisplayName("미오픈 공연을 꺼도 종료가 아니라 미오픈 그대로다")
    void turnOffNotOpened_StaysNotOpened() {
        // given
        Concert concert = save(ConcertCategory.STAGE, true);

        // when
        concert.changeCompanionEnabled(false);

        // then
        assertThat(reload(concert).getCompanionStatus()).isEqualTo(CompanionStatus.NOT_OPENED);
    }

    @Test
    @DisplayName("비공개 공연은 모집 중이어도 사용자에게 종료로 보이고, 다시 공개하면 모집 중으로 보인다")
    void unpublished_LooksClosedUntilPublishedAgain() {
        // given - 모집 중인 페스티벌을 비공개로 돌림
        Concert festival = save(ConcertCategory.FESTIVAL, true);
        festival.changePublished(false, LocalDateTime.now());

        // then
        assertThat(festival.companionStatusForUsers()).isEqualTo(CompanionStatus.CLOSED);
        assertThat(reload(festival).getCompanionStatus()).isEqualTo(CompanionStatus.OPEN); // 저장값은 그대로

        // when - 다시 공개
        festival = reload(festival);
        festival.changePublished(true, LocalDateTime.now());

        // then
        assertThat(festival.companionStatusForUsers()).isEqualTo(CompanionStatus.OPEN);
    }

    @Test
    @DisplayName("한 번도 연 적 없는 비공개 공연은 사용자에게 미오픈으로 보인다")
    void unpublishedNotOpened_LooksNotOpened() {
        assertThat(save(ConcertCategory.STAGE, false).companionStatusForUsers()).isEqualTo(CompanionStatus.NOT_OPENED);
    }

    @Test
    @DisplayName("공개 공연은 저장된 상태 그대로 사용자에게 보인다")
    void published_ShowsStoredStatus() {
        Concert concert = save(ConcertCategory.STAGE, true);
        concert.changeCompanionEnabled(true);
        concert.changeCompanionEnabled(false);

        assertThat(concert.companionStatusForUsers()).isEqualTo(CompanionStatus.CLOSED);
        assertThat(save(ConcertCategory.FESTIVAL, true).companionStatusForUsers()).isEqualTo(CompanionStatus.OPEN);
    }
}
