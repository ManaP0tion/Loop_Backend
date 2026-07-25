package com.loop.loop_backend.CompanionPost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.CompanionHeart.repository.CompanionHeartRepository;
import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.domain.WatchStyle;
import com.loop.loop_backend.CompanionPost.dto.CompanionDetailResponseDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionRequestDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.dto.PageResponseDto;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanionServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock ConcertRepository concertRepository;
    @Mock CompanionPostRepository companionPostRepository;
    @Mock UserHashtagRepository userHashtagRepository;
    @Mock BlockRepository blockRepository;
    @Mock CompanionHeartRepository companionHeartRepository;
    @InjectMocks CompanionServiceImpl companionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User user;
    private Concert concert;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();

        concert = stubConcert(10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        when(concertRepository.findById(10L)).thenReturn(Optional.of(concert));
        when(concertRepository.findById(999L)).thenReturn(Optional.empty());

        when(companionPostRepository.saveAndFlush(any(CompanionPost.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Concert stubConcert(long id) {
        Concert c = Concert.builder().title("테스트 콘서트").build();
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    private CompanionRequestDto requestDto(long concertId) throws Exception {
        String json = """
                {
                    "concertId": %d,
                    "watchDay": "DAY1",
                    "activities": ["MEAL"],
                    "watchStyle": "NORMAL",
                    "messageToCompanion": "같이 즐겁게 봐요!"
                }
                """.formatted(concertId);
        return objectMapper.readValue(json, CompanionRequestDto.class);
    }

    private CompanionPost buildPost(User owner, long concertId) {
        return CompanionPost.builder()
                .user(owner)
                .concert(stubConcert(concertId))
                .watchDay(WatchDay.DAY1)
                .activities(Set.of(CompanionActivity.MEAL))
                .watchStyle(WatchStyle.NORMAL)
                .build();
    }

    @Test
    void 정상_요청이면_동행_프로필이_저장된다() throws Exception {
        companionService.createCompanion(1L, requestDto(10L));

        ArgumentCaptor<CompanionPost> captor = ArgumentCaptor.forClass(CompanionPost.class);
        verify(companionPostRepository).saveAndFlush(captor.capture());

        CompanionPost saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getConcert().getId()).isEqualTo(10L);
        assertThat(saved.getWatchDay().name()).isEqualTo("DAY1");
        assertThat(saved.getWatchStyle().name()).isEqualTo("NORMAL");
    }

    @Test
    void 존재하지_않는_사용자면_USER_NOT_FOUND_예외를_던진다() {
        assertThatThrownBy(() -> companionService.createCompanion(999L, requestDto(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 존재하지_않는_콘서트면_CONCERT_NOT_FOUND_예외를_던진다() {
        assertThatThrownBy(() -> companionService.createCompanion(1L, requestDto(999L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONCERT_NOT_FOUND);
    }

    @Test
    void 같은_콘서트_같은_날짜에_이미_프로필이_있으면_COMPANION_POST_ALREADY_EXISTS_예외를_던진다() throws Exception {
        when(companionPostRepository.existsByUserAndConcert_IdAndWatchDay(any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> companionService.createCompanion(1L, requestDto(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_ALREADY_EXISTS);
    }

    @Test
    void 저장_시점에_유니크_제약_위반이_나도_COMPANION_POST_ALREADY_EXISTS_예외로_변환된다() throws Exception {
        when(companionPostRepository.saveAndFlush(any(CompanionPost.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> companionService.createCompanion(1L, requestDto(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_ALREADY_EXISTS);
    }

    // ── getWatchingCompanions ─────────────────────────────────────────────────

    private User buildUser(long id, String providerId) {
        User u = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId(providerId)
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private CompanionPost buildWatchingPost(User owner, Set<CompanionActivity> activities, WatchStyle watchStyle,
                                             LocalDateTime createdAt) {
        CompanionPost post = CompanionPost.builder()
                .user(owner)
                .concert(stubConcert(10L))
                .watchDay(WatchDay.DAY1)
                .activities(activities)
                .watchStyle(watchStyle)
                .build();
        ReflectionTestUtils.setField(post, "createdAt", createdAt);
        return post;
    }

    @Test
    void 내_프로필이_없으면_등록일자_최신순으로_정렬된다() {
        CompanionPost older = buildWatchingPost(buildUser(2L, "kakao-2"),
                Set.of(CompanionActivity.CONCERT), WatchStyle.NORMAL, LocalDateTime.now().minusDays(1));
        CompanionPost newer = buildWatchingPost(buildUser(3L, "kakao-3"),
                Set.of(CompanionActivity.CONCERT), WatchStyle.NORMAL, LocalDateTime.now());

        when(companionPostRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(older, newer));
        when(companionPostRepository.findByUser_IdAndConcert_IdAndWatchDay(1L, 10L, WatchDay.DAY1))
                .thenReturn(Optional.empty());

        PageResponseDto<CompanionResponseDto> result = companionService.getWatchingCompanions(
                1L, 10L, WatchDay.DAY1, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(CompanionResponseDto::getUserId)
                .containsExactly(3L, 2L);
    }

    @Test
    void 내_프로필이_있고_공연관람_미선택이면_공통활동_많은순으로_정렬된다() {
        CompanionPost myPost = buildWatchingPost(user,
                Set.of(CompanionActivity.MEAL, CompanionActivity.PHOTO), null, LocalDateTime.now());

        CompanionPost lowMatch = buildWatchingPost(buildUser(2L, "kakao-2"),
                Set.of(CompanionActivity.CONCERT, CompanionActivity.MEAL), WatchStyle.NORMAL, LocalDateTime.now());
        CompanionPost highMatch = buildWatchingPost(buildUser(3L, "kakao-3"),
                Set.of(CompanionActivity.CONCERT, CompanionActivity.MEAL, CompanionActivity.PHOTO),
                WatchStyle.QUIET, LocalDateTime.now());

        when(companionPostRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(lowMatch, highMatch));
        when(companionPostRepository.findByUser_IdAndConcert_IdAndWatchDay(1L, 10L, WatchDay.DAY1))
                .thenReturn(Optional.of(myPost));

        PageResponseDto<CompanionResponseDto> result = companionService.getWatchingCompanions(
                1L, 10L, WatchDay.DAY1, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(CompanionResponseDto::getUserId)
                .containsExactly(3L, 2L);
    }

    @Test
    void 내_프로필이_있고_공연관람_선택이면_관람스타일_동일_우선_공통활동_많은순으로_정렬된다() {
        CompanionPost myPost = buildWatchingPost(user,
                Set.of(CompanionActivity.CONCERT, CompanionActivity.MEAL), WatchStyle.QUIET, LocalDateTime.now());

        // 스타일은 다르지만 공통 활동이 더 많음
        CompanionPost differentStyleMoreCommon = buildWatchingPost(buildUser(2L, "kakao-2"),
                Set.of(CompanionActivity.CONCERT, CompanionActivity.MEAL, CompanionActivity.PHOTO),
                WatchStyle.NORMAL, LocalDateTime.now());
        // 스타일은 같지만 공통 활동이 더 적음
        CompanionPost sameStyleLessCommon = buildWatchingPost(buildUser(3L, "kakao-3"),
                Set.of(CompanionActivity.CONCERT), WatchStyle.QUIET, LocalDateTime.now());

        when(companionPostRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(differentStyleMoreCommon, sameStyleLessCommon));
        when(companionPostRepository.findByUser_IdAndConcert_IdAndWatchDay(1L, 10L, WatchDay.DAY1))
                .thenReturn(Optional.of(myPost));

        PageResponseDto<CompanionResponseDto> result = companionService.getWatchingCompanions(
                1L, 10L, WatchDay.DAY1, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(CompanionResponseDto::getUserId)
                .containsExactly(3L, 2L);
    }

    // gender/ageGroup 필터링은 CompanionPostSpecifications를 통해 쿼리 조건으로 내려가므로
    // 실제 필터링 동작 검증은 CompanionPostRepositoryTest(@DataJpaTest)에서 확인한다.

    // ── getNotWatchingCompanions ─────────────────────────────────────────────

    @Test
    void 미관람_목록에서_내_프로필이_없으면_등록일자_최신순으로_정렬된다() {
        CompanionPost older = buildWatchingPost(buildUser(2L, "kakao-2"),
                Set.of(CompanionActivity.MEAL), null, LocalDateTime.now().minusDays(1));
        CompanionPost newer = buildWatchingPost(buildUser(3L, "kakao-3"),
                Set.of(CompanionActivity.MEAL), null, LocalDateTime.now());

        when(companionPostRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(older, newer));
        when(companionPostRepository.findByUser_IdAndConcert_IdAndWatchDay(1L, 10L, WatchDay.DAY1))
                .thenReturn(Optional.empty());

        PageResponseDto<CompanionResponseDto> result = companionService.getNotWatchingCompanions(
                1L, 10L, WatchDay.DAY1, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(CompanionResponseDto::getUserId)
                .containsExactly(3L, 2L);
    }

    @Test
    void 미관람_목록에서_내_프로필이_있으면_관람스타일과_무관하게_공통활동_많은순으로_정렬된다() {
        CompanionPost myPost = buildWatchingPost(user,
                Set.of(CompanionActivity.MEAL, CompanionActivity.PHOTO), null, LocalDateTime.now());

        // 관람스타일이 나와 같아도 미관람 목록에서는 정렬에 영향을 주지 않는다
        CompanionPost sameStyleLessCommon = buildWatchingPost(buildUser(2L, "kakao-2"),
                Set.of(CompanionActivity.MEAL), null, LocalDateTime.now());
        CompanionPost highMatch = buildWatchingPost(buildUser(3L, "kakao-3"),
                Set.of(CompanionActivity.MEAL, CompanionActivity.PHOTO), null, LocalDateTime.now());

        when(companionPostRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(sameStyleLessCommon, highMatch));
        when(companionPostRepository.findByUser_IdAndConcert_IdAndWatchDay(1L, 10L, WatchDay.DAY1))
                .thenReturn(Optional.of(myPost));

        PageResponseDto<CompanionResponseDto> result = companionService.getNotWatchingCompanions(
                1L, 10L, WatchDay.DAY1, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(CompanionResponseDto::getUserId)
                .containsExactly(3L, 2L);
    }

    // ── getCompanion ──────────────────────────────────────────────────────────

    @Test
    void 공개_프로필은_소유자가_아니어도_조회된다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        CompanionResponseDto dto = companionService.getCompanion(999L, 1L);

        assertThat(dto.getConcertId()).isEqualTo(10L);
    }

    @Test
    void 비공개_프로필은_소유자_본인은_조회할_수_있다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        post.toggleVisible(false);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        CompanionResponseDto dto = companionService.getCompanion(1L, 1L);

        assertThat(dto.getConcertId()).isEqualTo(10L);
    }

    @Test
    void 비공개_프로필은_소유자가_아니면_FORBIDDEN_예외를_던진다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        post.toggleVisible(false);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> companionService.getCompanion(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 작성자와_차단_관계가_있으면_BLOCKED_USER_예외를_던진다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));
        when(blockRepository.existsBlockBetween(2L, List.of(1L))).thenReturn(true);

        assertThatThrownBy(() -> companionService.getCompanion(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLOCKED_USER);
    }

    @Test
    void 본인_프로필_조회시에는_차단_여부를_확인하지_않는다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        CompanionResponseDto dto = companionService.getCompanion(1L, 1L);

        assertThat(dto.getConcertId()).isEqualTo(10L);
        verify(blockRepository, never()).existsBlockBetween(any(), any());
    }

    @Test
    void 존재하지_않는_프로필을_조회하면_COMPANION_POST_NOT_FOUND_예외를_던진다() {
        when(companionPostRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.getCompanion(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_NOT_FOUND);
    }

    @Test
    void 작성자가_탈퇴한_프로필을_조회하면_WITHDRAWN_USER_예외를_던진다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        ReflectionTestUtils.setField(user, "status", Status.WITHDRAWN);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> companionService.getCompanion(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
    }

    // ── updateCompanion ───────────────────────────────────────────────────────

    @Test
    void 소유자는_프로필을_수정할_수_있다() throws Exception {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        CompanionResponseDto dto = companionService.updateCompanion(1L, 1L, requestDto(10L));

        assertThat(post.getMessageToCompanion()).isEqualTo("같이 즐겁게 봐요!");
        assertThat(dto.getConcertId()).isEqualTo(10L);
    }

    @Test
    void 프로필_수정시_콘서트나_관람일은_바뀌지_않는다() throws Exception {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        companionService.updateCompanion(1L, 1L, requestDto(999L));

        assertThat(post.getConcert().getId()).isEqualTo(10L);
        assertThat(post.getWatchDay()).isEqualTo(WatchDay.DAY1);
    }

    @Test
    void 소유자가_아니면_프로필_수정시_FORBIDDEN_예외를_던진다() throws Exception {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> companionService.updateCompanion(2L, 1L, requestDto(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 존재하지_않는_프로필을_수정하면_COMPANION_POST_NOT_FOUND_예외를_던진다() throws Exception {
        when(companionPostRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.updateCompanion(1L, 999L, requestDto(10L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_NOT_FOUND);
    }

    // ── updateVisibility ──────────────────────────────────────────────────────

    @Test
    void 소유자는_공개_여부를_변경할_수_있다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        companionService.updateVisibility(1L, 1L, false);

        assertThat(post.isVisible()).isFalse();
    }

    @Test
    void 소유자가_아니면_공개_여부_변경시_FORBIDDEN_예외를_던진다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> companionService.updateVisibility(2L, 1L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 존재하지_않는_프로필의_공개_여부를_변경하면_COMPANION_POST_NOT_FOUND_예외를_던진다() {
        when(companionPostRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.updateVisibility(1L, 999L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_NOT_FOUND);
    }

    // ── getMyCompanions ───────────────────────────────────────────────────────

    @Test
    void 내_동행_프로필_목록을_조회한다() {
        when(companionPostRepository.findAllByUser(user))
                .thenReturn(List.of(buildPost(user, 10L), buildPost(user, 20L)));

        List<CompanionResponseDto> result = companionService.getMyCompanions(1L);

        assertThat(result).hasSize(2)
                .extracting(CompanionResponseDto::getConcertId)
                .containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    void 등록한_동행_프로필이_없으면_COMPANION_POST_NOT_FOUND_예외를_던진다() {
        when(companionPostRepository.findAllByUser(user)).thenReturn(List.of());

        assertThatThrownBy(() -> companionService.getMyCompanions(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_NOT_FOUND);
    }

    @Test
    void 존재하지_않는_사용자의_동행_프로필을_조회하면_USER_NOT_FOUND_예외를_던진다() {
        assertThatThrownBy(() -> companionService.getMyCompanions(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── deleteCompanion ───────────────────────────────────────────────────────

    @Test
    void 소유자는_프로필을_삭제할_수_있다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        companionService.deleteCompanion(1L, 1L);

        verify(companionPostRepository).delete(post);
    }

    @Test
    void 소유자가_아니면_프로필_삭제시_FORBIDDEN_예외를_던진다() {
        ReflectionTestUtils.setField(user, "id", 1L);
        CompanionPost post = buildPost(user, 10L);
        when(companionPostRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> companionService.deleteCompanion(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void 존재하지_않는_프로필을_삭제하면_COMPANION_POST_NOT_FOUND_예외를_던진다() {
        when(companionPostRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.deleteCompanion(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_NOT_FOUND);
    }

    // ── existsMyCompanion ─────────────────────────────────────────────────────

    @Test
    void 대상_프로필과_같은_콘서트_날짜에_내_프로필이_있으면_true를_반환한다() {
        User otherUser = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-2")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
        CompanionPost targetPost = buildPost(otherUser, 10L);
        when(companionPostRepository.findById(2L)).thenReturn(Optional.of(targetPost));
        when(companionPostRepository.existsByUserAndConcert_IdAndWatchDay(user, 10L, WatchDay.DAY1))
                .thenReturn(true);

        assertThat(companionService.existsMyCompanion(1L, 2L)).isTrue();
    }

    @Test
    void 대상_프로필과_같은_콘서트_날짜에_내_프로필이_없으면_false를_반환한다() {
        CompanionPost targetPost = buildPost(user, 10L);
        when(companionPostRepository.findById(2L)).thenReturn(Optional.of(targetPost));
        when(companionPostRepository.existsByUserAndConcert_IdAndWatchDay(user, 10L, WatchDay.DAY1))
                .thenReturn(false);

        assertThat(companionService.existsMyCompanion(1L, 2L)).isFalse();
    }

    @Test
    void 존재하지_않는_대상_프로필로_확인하면_COMPANION_POST_NOT_FOUND_예외를_던진다() {
        when(companionPostRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.existsMyCompanion(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_NOT_FOUND);
    }

    @Test
    void 존재하지_않는_사용자로_확인하면_USER_NOT_FOUND_예외를_던진다() {
        CompanionPost targetPost = buildPost(user, 10L);
        when(companionPostRepository.findById(2L)).thenReturn(Optional.of(targetPost));

        assertThatThrownBy(() -> companionService.existsMyCompanion(999L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── countVisibleCompanions ────────────────────────────────────────────────
    // 필터 조합 자체(비공개/차단/탈퇴/동성공개)의 정확성은 CompanionPostRepositoryTest(@DataJpaTest)에서 검증한다.
    // 여기서는 이 메서드가 "조회자 기준으로 필터링된 리포지토리 카운트를 그대로 반환한다"는 위임 계약만 확인한다.

    @Test
    void 존재하지_않는_조회자로_카운트하면_USER_NOT_FOUND_예외를_던진다() {
        assertThatThrownBy(() -> companionService.countVisibleCompanions(10L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 조회자_기준으로_필터링된_리포지토리_카운트를_그대로_반환한다() {
        when(companionPostRepository.count(any(Specification.class))).thenReturn(7L);

        long count = companionService.countVisibleCompanions(10L, 1L);

        assertThat(count).isEqualTo(7L);
    }
}