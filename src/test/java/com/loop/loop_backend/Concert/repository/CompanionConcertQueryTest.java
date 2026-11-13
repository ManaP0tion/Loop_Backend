package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 동행 모집 공연 목록 요구사항:
// - 동행을 연 적 없는 공연(미오픈)은 빠지고, 모집 중·마감 공연이 나온다. 단독 공연과 페스티벌은 섞어서 보여준다.
// - 비공개 공연은 동행 OFF와 같이 마감으로 본다(저장값이 모집 중이어도). 연 적 없는 비공개 공연은 미오픈이라 빠진다.
// - 지난 공연은 빠지고 날짜 미정 공연은 포함된다.
// - 모집 중이 위, 마감이 아래. 각각 공연일 가까운 순, 날짜 미정은 맨 뒤.
// - 전체 수는 모집 중인 공연만 센다.
@DataJpaTest
class CompanionConcertQueryTest {

    @Autowired private ConcertRepository concertRepository;
    @Autowired private EntityManager em;

    private final LocalDate today = LocalDate.now();

    private Concert opened(String title, LocalDate start, LocalDate end) {
        return persist(title, start, end, true, Companion.OPEN);
    }

    private Concert closed(String title, LocalDate start, LocalDate end) {
        return persist(title, start, end, true, Companion.CLOSED);
    }

    private enum Companion { NEVER_OPENED, OPEN, CLOSED }

    // 관리자 조작으로 상태를 만든다: 단독 공연은 미오픈으로 시작 → ON하면 모집 중 → OFF하면 마감
    private Concert persist(String title, LocalDate start, LocalDate end, boolean published, Companion companion) {
        Concert concert = Concert.builder()
                .title(title)
                .category(ConcertCategory.STAGE)
                .startDate(start)
                .endDate(end)
                .published(published)
                .build();
        if (companion != Companion.NEVER_OPENED) concert.changeCompanionEnabled(true);
        if (companion == Companion.CLOSED) concert.changeCompanionEnabled(false);
        em.persist(concert);
        return concert;
    }

    private List<String> titles(Pageable pageable) {
        em.flush();
        em.clear();
        return concertRepository.findCompanionConcerts(today, pageable).stream().map(Concert::getTitle).toList();
    }

    @Test
    @DisplayName("미오픈 공연은 빠지고 모집 중·마감 공연은 나온다")
    void excludesNeverOpened() {
        opened("모집 중", today.plusDays(1), today.plusDays(1));
        closed("마감", today.plusDays(2), today.plusDays(2));
        persist("미오픈", today.plusDays(3), today.plusDays(3), true, Companion.NEVER_OPENED);

        assertThat(titles(Pageable.unpaged())).containsExactly("모집 중", "마감");
    }

    @Test
    @DisplayName("페스티벌은 처음부터 모집 중이라 단독 공연과 함께 나온다")
    void festivalAndStageMixed() {
        em.persist(Concert.builder().title("페스티벌").category(ConcertCategory.FESTIVAL)
                .startDate(today.plusDays(2)).endDate(today.plusDays(3)).published(true).build());
        opened("단독", today.plusDays(1), today.plusDays(1));

        assertThat(titles(Pageable.unpaged())).containsExactly("단독", "페스티벌");
    }

    @Test
    @DisplayName("모집 중이 위, 마감이 아래이고 각각 공연일 가까운 순이다")
    void openFirstThenClosed_EachByDate() {
        closed("마감-가까움", today.plusDays(1), today.plusDays(1));
        opened("모집-멀리", today.plusDays(20), today.plusDays(20));
        closed("마감-멀리", today.plusDays(10), today.plusDays(10));
        opened("모집-가까움", today.plusDays(5), today.plusDays(5));

        assertThat(titles(Pageable.unpaged()))
                .containsExactly("모집-가까움", "모집-멀리", "마감-가까움", "마감-멀리");
    }

    @Test
    @DisplayName("비공개로 바뀐 모집 중 공연은 마감 쪽(아래)에 나온다")
    void unpublishedOpen_SortedAsClosed() {
        persist("비공개-모집 중", today.plusDays(1), today.plusDays(1), false, Companion.OPEN);
        opened("공개-모집 중", today.plusDays(10), today.plusDays(10));

        assertThat(titles(Pageable.unpaged())).containsExactly("공개-모집 중", "비공개-모집 중");
    }

    @Test
    @DisplayName("동행을 연 적 없는 비공개 공연은 빠진다")
    void unpublishedNeverOpened_Excluded() {
        persist("비공개-미오픈", today.plusDays(1), today.plusDays(1), false, Companion.NEVER_OPENED);

        assertThat(titles(Pageable.unpaged())).isEmpty();
    }

    @Test
    @DisplayName("지난 공연은 빠지고, 종료일이 남은 공연과 날짜 미정 공연은 나온다. 날짜 미정은 같은 상태 안에서 맨 뒤")
    void excludesPast_IncludesOngoingAndUndated() {
        opened("지난 공연", today.minusDays(5), today.minusDays(3));
        opened("진행 중", today.minusDays(1), today.plusDays(1));
        opened("날짜 미정", null, null);
        opened("예정", today.plusDays(30), today.plusDays(30));
        closed("마감-예정", today.plusDays(1), today.plusDays(1));

        assertThat(titles(Pageable.unpaged()))
                .containsExactly("진행 중", "예정", "날짜 미정", "마감-예정");
    }

    @Test
    @DisplayName("size만큼만 앞에서부터 가져온다")
    void limitsBySize() {
        opened("1", today.plusDays(1), today.plusDays(1));
        opened("2", today.plusDays(2), today.plusDays(2));
        closed("3", today.plusDays(3), today.plusDays(3));

        assertThat(titles(PageRequest.of(0, 2))).containsExactly("1", "2");
    }

    @Test
    @DisplayName("전체 수는 모집 중인 공연만 센다(마감·비공개·미오픈·지난 공연 제외)")
    void countsOpenOnly() {
        opened("모집 중1", today.plusDays(1), today.plusDays(1));
        opened("모집 중-날짜 미정", null, null);
        closed("마감", today.plusDays(1), today.plusDays(1));
        persist("비공개-모집 중", today.plusDays(1), today.plusDays(1), false, Companion.OPEN);
        persist("미오픈", today.plusDays(1), today.plusDays(1), true, Companion.NEVER_OPENED);
        opened("지난 공연", today.minusDays(5), today.minusDays(5));
        em.flush();
        em.clear();

        assertThat(concertRepository.countCompanionOpen(today)).isEqualTo(2);
    }
}
