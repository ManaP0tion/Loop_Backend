package com.loop.loop_backend.CompanionPost.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.PreferredAgeGroup;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.domain.WatchStyle;
import com.loop.loop_backend.CompanionPost.dto.CompanionDetailResponseDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionRequestDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanionServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock ConcertRepository concertRepository;
    @Mock CompanionPostRepository companionPostRepository;
    @Mock UserHashtagRepository userHashtagRepository;
    @InjectMocks CompanionServiceImpl companionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        when(concertRepository.existsById(10L)).thenReturn(true);
        when(concertRepository.existsById(999L)).thenReturn(false);

        when(companionPostRepository.saveAndFlush(any(CompanionPost.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CompanionRequestDto requestDto(long concertId) throws Exception {
        String json = """
                {
                    "concertId": %d,
                    "watchDay": "DAY1",
                    "preferredGender": "ANY",
                    "preferredAgeGroups": ["TWENTY_FIVE_TO_TWENTY_NINE"],
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
                .concertId(concertId)
                .watchDay(WatchDay.DAY1)
                .preferredAgeGroups(Set.of(PreferredAgeGroup.TWENTY_FIVE_TO_TWENTY_NINE))
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
        assertThat(saved.getConcertId()).isEqualTo(10L);
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
        when(companionPostRepository.existsByUserAndConcertIdAndWatchDay(any(), any(), any()))
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

    // ── getCompanion ──────────────────────────────────────────────────────────

    @Test
    void 공개_프로필은_소유자가_아니어도_조회된다() {
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
    void 존재하지_않는_프로필을_조회하면_COMPANION_POST_NOT_FOUND_예외를_던진다() {
        when(companionPostRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companionService.getCompanion(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_POST_NOT_FOUND);
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

        assertThat(post.getConcertId()).isEqualTo(10L);
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
        when(companionPostRepository.existsByUserAndConcertIdAndWatchDay(user, 10L, WatchDay.DAY1))
                .thenReturn(true);

        assertThat(companionService.existsMyCompanion(1L, 2L)).isTrue();
    }

    @Test
    void 대상_프로필과_같은_콘서트_날짜에_내_프로필이_없으면_false를_반환한다() {
        CompanionPost targetPost = buildPost(user, 10L);
        when(companionPostRepository.findById(2L)).thenReturn(Optional.of(targetPost));
        when(companionPostRepository.existsByUserAndConcertIdAndWatchDay(user, 10L, WatchDay.DAY1))
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
}