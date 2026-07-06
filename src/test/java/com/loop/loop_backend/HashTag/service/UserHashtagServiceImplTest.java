package com.loop.loop_backend.HashTag.service;

import com.loop.loop_backend.HashTag.domain.UserHashtag;
import com.loop.loop_backend.HashTag.dto.HashtagResponseDto;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserHashtagServiceImplTest {

    @Mock UserHashtagRepository userHashtagRepository;
    @Mock UserRepository userRepository;
    @InjectMocks UserHashtagServiceImpl userHashtagService;

    private static final Long USER_ID = 1L;

    private User testUser() {
        return User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
    }

    // ── getHashtags ──────────────────────────────────────────────────────────

    @Test
    void 등록된_해시태그_목록을_그대로_반환한다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userHashtagRepository.findAllByUser(user)).thenReturn(List.of(
                UserHashtag.builder().user(user).tag("발라드").build(),
                UserHashtag.builder().user(user).tag("힙합").build()
        ));

        List<HashtagResponseDto> result = userHashtagService.getHashtags(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(HashtagResponseDto::getTag)
                .containsExactly("발라드", "힙합");
    }

    @Test
    void 해시태그가_하나도_없으면_빈_목록을_반환한다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userHashtagRepository.findAllByUser(user)).thenReturn(List.of());

        List<HashtagResponseDto> result = userHashtagService.getHashtags(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void 존재하지_않는_유저_조회시_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userHashtagService.getHashtags(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── addHashtag ───────────────────────────────────────────────────────────

    @Test
    void 해시태그를_추가하면_저장된_해시태그를_반환한다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userHashtagRepository.countByUser(user)).thenReturn(0);
        when(userHashtagRepository.existsByUserAndTag(user, "맛집")).thenReturn(false);
        when(userHashtagRepository.save(any(UserHashtag.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HashtagResponseDto result = userHashtagService.addHashtag(USER_ID, "맛집");

        assertThat(result.getTag()).isEqualTo("맛집");
    }

    @Test
    void 태그_앞뒤_공백은_제거하고_저장한다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userHashtagRepository.countByUser(user)).thenReturn(0);
        when(userHashtagRepository.existsByUserAndTag(eq(user), any())).thenReturn(false);
        when(userHashtagRepository.save(any(UserHashtag.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HashtagResponseDto result = userHashtagService.addHashtag(USER_ID, "  맛집  ");

        assertThat(result.getTag()).isEqualTo("맛집");
    }

    @Test
    void 이미_3개_등록된_경우_LIMIT_HASHTAG_예외를_던지고_저장하지_않는다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userHashtagRepository.countByUser(user)).thenReturn(3);

        assertThatThrownBy(() -> userHashtagService.addHashtag(USER_ID, "새태그"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LIMIT_HASHTAG);

        verify(userHashtagRepository, never()).save(any());
    }

    @Test
    void 이미_등록된_태그를_추가하면_DUPLICATE_HASHTAG_예외를_던지고_저장하지_않는다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userHashtagRepository.countByUser(user)).thenReturn(1);
        when(userHashtagRepository.existsByUserAndTag(user, "맛집")).thenReturn(true);

        assertThatThrownBy(() -> userHashtagService.addHashtag(USER_ID, "맛집"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_HASHTAG);

        verify(userHashtagRepository, never()).save(any());
    }

    @Test
    void 존재하지_않는_유저에게_추가시_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userHashtagService.addHashtag(USER_ID, "맛집"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userHashtagRepository, never()).save(any());
    }

    // ── deleteHashtag ────────────────────────────────────────────────────────

    @Test
    void 본인_소유_해시태그는_정상적으로_삭제된다() {
        User user = testUser();
        UserHashtag hashtag = UserHashtag.builder().user(user).tag("맛집").build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userHashtagRepository.findByIdAndUser(10L, user)).thenReturn(Optional.of(hashtag));

        userHashtagService.deleteHashtag(USER_ID, 10L);

        verify(userHashtagRepository).delete(hashtag);
    }

    @Test
    void 존재하지_않거나_본인_소유가_아닌_해시태그_삭제시_HASHTAG_NOT_FOUND_예외를_던진다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userHashtagRepository.findByIdAndUser(999L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userHashtagService.deleteHashtag(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.HASHTAG_NOT_FOUND);

        verify(userHashtagRepository, never()).delete(any());
    }

    @Test
    void 존재하지_않는_유저의_해시태그_삭제시_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userHashtagService.deleteHashtag(USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userHashtagRepository, never()).delete(any());
    }
}