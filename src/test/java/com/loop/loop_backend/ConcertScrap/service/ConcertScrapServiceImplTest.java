package com.loop.loop_backend.ConcertScrap.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.ConcertScrap.domain.ConcertScrap;
import com.loop.loop_backend.ConcertScrap.repository.ConcertScrapRepository;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.time.ExpiryCutoff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// 스크랩 요구사항:
// - 스크랩/취소는 토글 UX라서 이미 스크랩한 공연을 다시 스크랩하거나, 스크랩 안 한 공연을 취소해도 에러 없이 성공한다.
// - 존재하지 않는 공연은 스크랩할 수 없다(404).
// - 목록은 예정/지난을 따로 조회하며, 공연 목록과 같은 만료 기준일을 쓴다.
// 예정/지난 분류와 정렬 자체는 ConcertScrapRepositoryTest에서 실제 쿼리로 검증한다.
@ExtendWith(MockitoExtension.class)
class ConcertScrapServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long CONCERT_ID = 10L;

    @Mock ConcertScrapRepository concertScrapRepository;
    @Mock ConcertRepository concertRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ConcertScrapServiceImpl concertScrapService;

    private Concert concert(Long id, String title) {
        Concert concert = Concert.builder()
                .title(title)
                .category(ConcertCategory.J_POP_ARTIST)
                .startDate(LocalDate.of(2026, 12, 1))
                .endDate(LocalDate.of(2026, 12, 1))
                .build();
        ReflectionTestUtils.setField(concert, "id", id);
        return concert;
    }

    private User user() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    // ---------- scrap ----------

    @Test
    void 스크랩하지_않은_공연을_스크랩하면_내_스크랩으로_저장된다() {
        Concert concert = concert(CONCERT_ID, "공연");
        User user = user();
        when(concertRepository.findById(CONCERT_ID)).thenReturn(Optional.of(concert));
        when(concertScrapRepository.existsByUser_IdAndConcert_Id(USER_ID, CONCERT_ID)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        concertScrapService.scrap(USER_ID, CONCERT_ID);

        ArgumentCaptor<ConcertScrap> captor = ArgumentCaptor.forClass(ConcertScrap.class);
        verify(concertScrapRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getConcert()).isSameAs(concert);
    }

    @Test
    void 이미_스크랩한_공연을_다시_스크랩해도_에러_없이_성공하고_중복_저장하지_않는다() {
        when(concertRepository.findById(CONCERT_ID)).thenReturn(Optional.of(concert(CONCERT_ID, "공연")));
        when(concertScrapRepository.existsByUser_IdAndConcert_Id(USER_ID, CONCERT_ID)).thenReturn(true);

        concertScrapService.scrap(USER_ID, CONCERT_ID);

        verify(concertScrapRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_공연을_스크랩하면_CONCERT_NOT_FOUND이고_저장하지_않는다() {
        when(concertRepository.findById(CONCERT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> concertScrapService.scrap(USER_ID, CONCERT_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONCERT_NOT_FOUND));
        verify(concertScrapRepository, never()).save(any());
    }

    // ---------- unscrap ----------

    @Test
    void 스크랩한_공연을_취소하면_스크랩이_삭제된다() {
        ConcertScrap scrap = ConcertScrap.builder().user(user()).concert(concert(CONCERT_ID, "공연")).build();
        when(concertScrapRepository.findByUser_IdAndConcert_Id(USER_ID, CONCERT_ID)).thenReturn(Optional.of(scrap));

        concertScrapService.unscrap(USER_ID, CONCERT_ID);

        verify(concertScrapRepository).delete(scrap);
    }

    @Test
    void 스크랩하지_않은_공연을_취소해도_에러_없이_성공한다() {
        when(concertScrapRepository.findByUser_IdAndConcert_Id(USER_ID, CONCERT_ID)).thenReturn(Optional.empty());

        concertScrapService.unscrap(USER_ID, CONCERT_ID);

        verify(concertScrapRepository, never()).delete(any());
    }

    // ---------- getMyScraps ----------

    @Test
    void 예정_스크랩_목록은_공연_목록과_같은_만료_기준일로_조회한_예정_공연을_순서대로_돌려준다() {
        when(concertScrapRepository.findUpcomingOrUndatedScrappedConcerts(eq(USER_ID), eq(ExpiryCutoff.cutoffDate())))
                .thenReturn(List.of(concert(1L, "가까운 공연"), concert(2L, "먼 공연")));

        List<ConcertSummaryDto> result = concertScrapService.getMyScraps(USER_ID, ConcertPeriod.UPCOMING);

        assertThat(result).extracting(ConcertSummaryDto::getConcertId).containsExactly(1L, 2L);
        assertThat(result).extracting(ConcertSummaryDto::getTitle).containsExactly("가까운 공연", "먼 공연");
        verify(concertScrapRepository, never()).findPastScrappedConcerts(any(), any());
    }

    @Test
    void 지난_스크랩_목록은_공연_목록과_같은_만료_기준일로_조회한_지난_공연을_순서대로_돌려준다() {
        when(concertScrapRepository.findPastScrappedConcerts(eq(USER_ID), eq(ExpiryCutoff.cutoffDate())))
                .thenReturn(List.of(concert(3L, "최근 종료"), concert(4L, "오래전 종료")));

        List<ConcertSummaryDto> result = concertScrapService.getMyScraps(USER_ID, ConcertPeriod.PAST);

        assertThat(result).extracting(ConcertSummaryDto::getConcertId).containsExactly(3L, 4L);
        verify(concertScrapRepository, never()).findUpcomingOrUndatedScrappedConcerts(any(), any());
    }
}