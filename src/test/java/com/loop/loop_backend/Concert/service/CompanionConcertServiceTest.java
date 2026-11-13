package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Concert.domain.CompanionStatus;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.CompanionConcertDto;
import com.loop.loop_backend.Concert.dto.CompanionConcertListDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 동행 모집 공연 목록 요구사항 (조회 조건·정렬·전체 수는 CompanionConcertQueryTest):
// - 비로그인도 볼 수 있고, 이때 내 프로필 등록 여부는 null
// - 로그인하면 공연마다 관람일이 지나지 않은 내 동행 프로필이 있는지 알려준다(DAY 무관)
// - 동행 상태는 사용자에게 보이는 상태: 비공개 공연은 저장값이 모집 중이어도 마감
// - size는 미리보기용 개수. 생략하면 전체, 1보다 작으면 잘못된 요청
// - 전체 수는 size와 상관없이 모집 중인 공연 수
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanionConcertServiceTest {

    private static final Long USER_ID = 7L;

    @Mock ConcertRepository concertRepository;
    @Mock CompanionPostRepository companionPostRepository;
    @InjectMocks ConcertServiceImpl concertService;

    private final LocalDate today = LocalDate.now();

    private Concert concert(long id, LocalDate start, LocalDate end, boolean published) {
        Concert concert = Concert.builder()
                .title("공연" + id)
                .category(ConcertCategory.FESTIVAL)
                .startDate(start)
                .endDate(end)
                .published(published)
                .build();
        ReflectionTestUtils.setField(concert, "id", id);
        concert.changeCompanionEnabled(true);
        return concert;
    }

    private CompanionPost myPost(Concert concert, WatchDay watchDay) {
        return CompanionPost.builder().concert(concert).watchDay(watchDay).build();
    }

    private void listReturns(List<Concert> concerts, long openCount) {
        when(concertRepository.findCompanionConcerts(any(), any())).thenReturn(concerts);
        when(concertRepository.countCompanionOpen(any())).thenReturn(openCount);
    }

    @Test
    @DisplayName("비로그인이면 내 프로필 등록 여부가 null이고 동행 프로필을 조회하지 않는다")
    void anonymous_RegisteredIsNull() {
        listReturns(List.of(concert(1L, today.plusDays(1), today.plusDays(1), true)), 1);

        CompanionConcertListDto result = concertService.getCompanionOpenConcerts(null, null);

        assertThat(result.concerts()).extracting(CompanionConcertDto::myProfileRegistered).containsOnlyNulls();
        verifyNoInteractions(companionPostRepository);
    }

    @Test
    @DisplayName("로그인하면 공연마다 내 프로필이 있으면 true, 없으면 false")
    void loggedIn_RegisteredPerConcert() {
        Concert mine = concert(1L, today.plusDays(1), today.plusDays(1), true);
        Concert notMine = concert(2L, today.plusDays(2), today.plusDays(2), true);
        listReturns(List.of(mine, notMine), 2);
        when(companionPostRepository.findAllByUser_IdAndConcert_IdIn(anyLong(), anyCollection()))
                .thenReturn(List.of(myPost(mine, WatchDay.DAY1)));

        CompanionConcertListDto result = concertService.getCompanionOpenConcerts(null, USER_ID);

        assertThat(result.concerts()).extracting(CompanionConcertDto::concertId, CompanionConcertDto::myProfileRegistered)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, true),
                        org.assertj.core.groups.Tuple.tuple(2L, false));
        verify(companionPostRepository).findAllByUser_IdAndConcert_IdIn(
                argThat(id -> id.equals(USER_ID)), argThat(ids -> ids.containsAll(List.of(1L, 2L))));
    }

    @Test
    @DisplayName("진행 중인 페스티벌에서 관람일이 지난 DAY 프로필만 있으면 false, 남은 DAY 프로필이 있으면 true")
    void expiredDayProfile_NotCounted() {
        // 3일 전 시작한 4일짜리 페스티벌: DAY1은 3일 전(만료), DAY4는 오늘(유효)
        Concert onlyExpired = concert(1L, today.minusDays(3), today, true);
        Concert hasUpcomingDay = concert(2L, today.minusDays(3), today, true);
        listReturns(List.of(onlyExpired, hasUpcomingDay), 2);
        when(companionPostRepository.findAllByUser_IdAndConcert_IdIn(anyLong(), anyCollection()))
                .thenReturn(List.of(
                        myPost(onlyExpired, WatchDay.DAY1),
                        myPost(hasUpcomingDay, WatchDay.DAY1),
                        myPost(hasUpcomingDay, WatchDay.DAY4)));

        CompanionConcertListDto result = concertService.getCompanionOpenConcerts(null, USER_ID);

        assertThat(result.concerts()).extracting(CompanionConcertDto::myProfileRegistered).containsExactly(false, true);
    }

    @Test
    @DisplayName("동행 상태는 사용자에게 보이는 상태다: 공개 모집 중은 OPEN, 비공개로 바뀐 모집 중은 CLOSED")
    void companionStatus_AsSeenByUsers() {
        listReturns(List.of(
                concert(1L, today.plusDays(1), today.plusDays(1), true),
                concert(2L, today.plusDays(1), today.plusDays(1), false)), 1);

        CompanionConcertListDto result = concertService.getCompanionOpenConcerts(null, null);

        assertThat(result.concerts()).extracting(CompanionConcertDto::companionStatus)
                .containsExactly(CompanionStatus.OPEN, CompanionStatus.CLOSED);
    }

    @Test
    @DisplayName("전체 수는 size와 상관없이 모집 중인 공연 수다")
    void totalCount_IndependentOfSize() {
        listReturns(List.of(concert(1L, today.plusDays(1), today.plusDays(1), true)), 5);

        CompanionConcertListDto result = concertService.getCompanionOpenConcerts(1, null);

        assertThat(result.totalCount()).isEqualTo(5);
        assertThat(result.concerts()).hasSize(1);
    }

    @Test
    @DisplayName("size를 주면 그 개수만큼, 생략하면 전체를 요청한다")
    void size_PassedAsPage() {
        listReturns(List.of(), 0);

        concertService.getCompanionOpenConcerts(3, null);
        verify(concertRepository).findCompanionConcerts(any(), argThat(p -> p.isPaged() && p.getPageSize() == 3));

        concertService.getCompanionOpenConcerts(null, null);
        verify(concertRepository).findCompanionConcerts(any(), argThat(Pageable::isUnpaged));
    }

    @Test
    @DisplayName("size가 1보다 작으면 잘못된 요청이다")
    void sizeBelowOne_Rejected() {
        assertThatThrownBy(() -> concertService.getCompanionOpenConcerts(0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
