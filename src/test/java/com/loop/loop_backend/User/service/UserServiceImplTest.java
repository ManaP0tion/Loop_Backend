package com.loop.loop_backend.User.service;

import com.loop.loop_backend.Chat.service.ChatService;
import com.loop.loop_backend.FavoriteArtist.repository.FavoriteArtistRepository;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.auth.service.RefreshTokenService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock UserHashtagRepository userHashtagRepository;
    @Mock FavoriteArtistRepository favoriteArtistRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock S3StorageService s3StorageService;
    @Mock ChatService chatService;
    @Mock RefreshTokenService refreshTokenService;
    @InjectMocks UserServiceImpl userService;

    private static final Long USER_ID = 1L;

    private User testUser() {
        return User.builder()
                .authProvider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .status(Status.ACTIVE)
                .onboardingCompleted(true)
                .build();
    }

    // ── withdrawUser ─────────────────────────────────────────────────────

    @Test
    void 탈퇴하면_상태가_WITHDRAWN으로_바뀌고_채팅_탈퇴처리와_리프레시_토큰_삭제가_호출된다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.withdrawUser(USER_ID);

        assertThat(user.getStatus()).isEqualTo(Status.WITHDRAWN);
        verify(chatService).handleUserWithdrawn(USER_ID);
        verify(refreshTokenService).delete(USER_ID);
    }

    @Test
    void 존재하지_않는_사용자를_탈퇴처리하면_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdrawUser(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
