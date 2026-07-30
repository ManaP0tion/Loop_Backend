package com.loop.loop_backend.CompanionHeart.service;

import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.CompanionHeart.domain.CompanionHeart;
import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertDetailDto;
import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertSummaryDto;
import com.loop.loop_backend.CompanionHeart.repository.CompanionHeartRepository;
import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanionHeartServiceImplTest {

    @Mock CompanionHeartRepository companionHeartRepository;
    @Mock CompanionPostRepository companionPostRepository;
    @Mock ConcertRepository concertRepository;
    @Mock UserRepository userRepository;
    @Mock BlockRepository blockRepository;
    @InjectMocks CompanionHeartServiceImpl companionHeartService;

    private static final Long VIEWER_ID = 1L;
    private static final Long AUTHOR_ID = 2L;
    private static final Long COMPANION_ID = 10L;

    private User testUser(long id) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-" + id)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Concert testConcert(long id, String title) {
        return testConcert(id, title, null);
    }

    private Concert testConcert(long id, String title, LocalDate startDate) {
        Concert concert = Concert.builder().title(title).category(ConcertCategory.DOMESTIC_ARTIST)
                .startDate(startDate).venue(title + " 공연장").build();
        ReflectionTestUtils.setField(concert, "id", id);
        return concert;
    }

    private CompanionPost testPost(long id, User author, Concert concert, WatchDay watchDay) {
        CompanionPost post = CompanionPost.builder()
                .user(author)
                .concert(concert)
                .watchDay(watchDay)
                .activities(Set.of(CompanionActivity.MEAL))
                .build();
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    // ── heartCompanion ───────────────────────────────────────────────────

    @Test
    void 하트를_누르면_저장된다() {
        User author = testUser(AUTHOR_ID);
        Concert concert = testConcert(100L, "콘서트1");
        CompanionPost post = testPost(COMPANION_ID, author, concert, WatchDay.DAY1);
        User viewer = testUser(VIEWER_ID);

        when(companionPostRepository.findById(COMPANION_ID)).thenReturn(Optional.of(post));
        when(companionHeartRepository.existsByUser_IdAndCompanionPost_Id(VIEWER_ID, COMPANION_ID)).thenReturn(false);
        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(viewer));

        companionHeartService.heartCompanion(VIEWER_ID, COMPANION_ID);

        verify(companionHeartRepository).save(any(CompanionHeart.class));
    }

    @Test
    void 본인_글에_하트를_누르면_SELF_HEART_NOT_ALLOWED_예외를_던진다() {
        User author = testUser(AUTHOR_ID);
        Concert concert = testConcert(100L, "콘서트1");
        CompanionPost post = testPost(COMPANION_ID, author, concert, WatchDay.DAY1);

        when(companionPostRepository.findById(COMPANION_ID)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> companionHeartService.heartCompanion(AUTHOR_ID, COMPANION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SELF_HEART_NOT_ALLOWED);

        verify(companionHeartRepository, never()).save(any());
    }

    @Test
    void 이미_하트한_상태에서_다시_누르면_에러_없이_저장도_하지_않는다() {
        User author = testUser(AUTHOR_ID);
        Concert concert = testConcert(100L, "콘서트1");
        CompanionPost post = testPost(COMPANION_ID, author, concert, WatchDay.DAY1);

        when(companionPostRepository.findById(COMPANION_ID)).thenReturn(Optional.of(post));
        when(companionHeartRepository.existsByUser_IdAndCompanionPost_Id(VIEWER_ID, COMPANION_ID)).thenReturn(true);

        companionHeartService.heartCompanion(VIEWER_ID, COMPANION_ID);

        verify(companionHeartRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_글에_하트를_누르면_COMPANION_POST_NOT_FOUND_예외를_던진다() {
        when(companionPostRepository.findById(COMPANION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companionHeartService.heartCompanion(VIEWER_ID, COMPANION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_NOT_FOUND);
    }

    // ── unheartCompanion ─────────────────────────────────────────────────

    @Test
    void 하트_취소시_기존_하트를_삭제한다() {
        User viewer = testUser(VIEWER_ID);
        User author = testUser(AUTHOR_ID);
        Concert concert = testConcert(100L, "콘서트1");
        CompanionPost post = testPost(COMPANION_ID, author, concert, WatchDay.DAY1);
        CompanionHeart heart = CompanionHeart.builder().user(viewer).companionPost(post).build();

        when(companionHeartRepository.findByUser_IdAndCompanionPost_Id(VIEWER_ID, COMPANION_ID))
                .thenReturn(Optional.of(heart));

        companionHeartService.unheartCompanion(VIEWER_ID, COMPANION_ID);

        verify(companionHeartRepository).delete(heart);
    }

    @Test
    void 하트하지_않은_상태에서_취소해도_에러없이_아무일도_일어나지_않는다() {
        when(companionHeartRepository.findByUser_IdAndCompanionPost_Id(VIEWER_ID, COMPANION_ID))
                .thenReturn(Optional.empty());

        companionHeartService.unheartCompanion(VIEWER_ID, COMPANION_ID);

        verify(companionHeartRepository, never()).delete(any());
    }

    // ── getMyHeartedConcerts ─────────────────────────────────────────────

    @Test
    void 하트한_프로필들을_콘서트별로_묶고_최대_2개_미리보기와_전체_개수를_반환한다() {
        User viewer = testUser(VIEWER_ID);
        User author = testUser(AUTHOR_ID);
        Concert concertA = testConcert(100L, "콘서트A");
        Concert concertB = testConcert(200L, "콘서트B");

        CompanionPost postA1 = testPost(11L, author, concertA, WatchDay.DAY1);
        CompanionPost postA2 = testPost(12L, author, concertA, WatchDay.DAY2);
        CompanionPost postA3 = testPost(13L, author, concertA, WatchDay.DAY1);
        CompanionPost postB1 = testPost(21L, author, concertB, WatchDay.DAY1);

        List<CompanionHeart> hearts = List.of(
                CompanionHeart.builder().user(viewer).companionPost(postA1).build(),
                CompanionHeart.builder().user(viewer).companionPost(postA2).build(),
                CompanionHeart.builder().user(viewer).companionPost(postA3).build(),
                CompanionHeart.builder().user(viewer).companionPost(postB1).build()
        );
        when(companionHeartRepository.findAllByUser_IdOrderByCreatedAtDesc(VIEWER_ID)).thenReturn(hearts);

        List<HeartedConcertSummaryDto> result = companionHeartService.getMyHeartedConcerts(VIEWER_ID);

        assertThat(result).hasSize(2);
        HeartedConcertSummaryDto concertAGroup = result.stream()
                .filter(g -> g.getConcertId().equals(100L)).findFirst().orElseThrow();
        assertThat(concertAGroup.getHeartCount()).isEqualTo(3);
        assertThat(concertAGroup.getCompanions()).hasSize(2);
        assertThat(concertAGroup.getVenue()).isEqualTo("콘서트A 공연장");

        HeartedConcertSummaryDto concertBGroup = result.stream()
                .filter(g -> g.getConcertId().equals(200L)).findFirst().orElseThrow();
        assertThat(concertBGroup.getHeartCount()).isEqualTo(1);
        assertThat(concertBGroup.getCompanions()).hasSize(1);
    }

    @Test
    void 작성자가_비공개로_전환한_프로필은_하트탭에서_제외된다() {
        User viewer = testUser(VIEWER_ID);
        User author = testUser(AUTHOR_ID);
        Concert concert = testConcert(100L, "콘서트A");

        CompanionPost visiblePost = testPost(11L, author, concert, WatchDay.DAY1);
        CompanionPost hiddenPost = testPost(12L, author, concert, WatchDay.DAY1);
        hiddenPost.toggleVisible(false);

        List<CompanionHeart> hearts = List.of(
                CompanionHeart.builder().user(viewer).companionPost(visiblePost).build(),
                CompanionHeart.builder().user(viewer).companionPost(hiddenPost).build()
        );
        when(companionHeartRepository.findAllByUser_IdOrderByCreatedAtDesc(VIEWER_ID)).thenReturn(hearts);

        List<HeartedConcertSummaryDto> result = companionHeartService.getMyHeartedConcerts(VIEWER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHeartCount()).isEqualTo(1);
    }

    @Test
    void 작성자가_탈퇴한_프로필은_하트탭에서_제외된다() {
        User viewer = testUser(VIEWER_ID);
        User author = testUser(AUTHOR_ID);
        ReflectionTestUtils.setField(author, "status", Status.WITHDRAWN);
        Concert concert = testConcert(100L, "콘서트A");
        CompanionPost post = testPost(11L, author, concert, WatchDay.DAY1);

        when(companionHeartRepository.findAllByUser_IdOrderByCreatedAtDesc(VIEWER_ID))
                .thenReturn(List.of(CompanionHeart.builder().user(viewer).companionPost(post).build()));

        List<HeartedConcertSummaryDto> result = companionHeartService.getMyHeartedConcerts(VIEWER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void 차단_관계가_있는_작성자의_프로필은_하트탭에서_제외된다() {
        User viewer = testUser(VIEWER_ID);
        User author = testUser(AUTHOR_ID);
        Concert concert = testConcert(100L, "콘서트A");
        CompanionPost post = testPost(11L, author, concert, WatchDay.DAY1);

        when(companionHeartRepository.findAllByUser_IdOrderByCreatedAtDesc(VIEWER_ID))
                .thenReturn(List.of(CompanionHeart.builder().user(viewer).companionPost(post).build()));
        when(blockRepository.findBlockedRelatedUserIds(VIEWER_ID, List.of(AUTHOR_ID)))
                .thenReturn(List.of(AUTHOR_ID));

        List<HeartedConcertSummaryDto> result = companionHeartService.getMyHeartedConcerts(VIEWER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void 관람일이_지난_프로필은_하트탭에서_제외된다() {
        User viewer = testUser(VIEWER_ID);
        User author = testUser(AUTHOR_ID);
        Concert pastConcert = testConcert(100L, "콘서트A", LocalDate.now().minusDays(2));
        Concert upcomingConcert = testConcert(200L, "콘서트B", LocalDate.now().plusDays(1));

        CompanionPost expiredPost = testPost(11L, author, pastConcert, WatchDay.DAY1);
        CompanionPost activePost = testPost(21L, author, upcomingConcert, WatchDay.DAY1);

        when(companionHeartRepository.findAllByUser_IdOrderByCreatedAtDesc(VIEWER_ID))
                .thenReturn(List.of(
                        CompanionHeart.builder().user(viewer).companionPost(expiredPost).build(),
                        CompanionHeart.builder().user(viewer).companionPost(activePost).build()
                ));

        List<HeartedConcertSummaryDto> result = companionHeartService.getMyHeartedConcerts(VIEWER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConcertId()).isEqualTo(200L);
    }

    // ── getMyHeartedCompanionsByConcert ──────────────────────────────────

    @Test
    void 콘서트_상세_조회시_관람일별로_묶어서_반환한다() {
        User viewer = testUser(VIEWER_ID);
        User author = testUser(AUTHOR_ID);
        Concert concert = testConcert(100L, "콘서트A");

        CompanionPost postDay1 = testPost(11L, author, concert, WatchDay.DAY1);
        CompanionPost postDay2 = testPost(12L, author, concert, WatchDay.DAY2);

        when(concertRepository.findById(100L)).thenReturn(Optional.of(concert));
        when(companionHeartRepository.findAllByUser_IdAndCompanionPost_Concert_Id(VIEWER_ID, 100L))
                .thenReturn(List.of(
                        CompanionHeart.builder().user(viewer).companionPost(postDay1).build(),
                        CompanionHeart.builder().user(viewer).companionPost(postDay2).build()
                ));

        HeartedConcertDetailDto result = companionHeartService.getMyHeartedCompanionsByConcert(VIEWER_ID, 100L);

        assertThat(result.getConcertId()).isEqualTo(100L);
        assertThat(result.getDays()).containsOnlyKeys(WatchDay.DAY1, WatchDay.DAY2);
        assertThat(result.getDays().get(WatchDay.DAY1)).hasSize(1);
    }

    @Test
    void 존재하지_않는_콘서트_상세_조회시_CONCERT_NOT_FOUND_예외를_던진다() {
        when(concertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companionHeartService.getMyHeartedCompanionsByConcert(VIEWER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONCERT_NOT_FOUND);
    }
}