package com.loop.loop_backend.User.service;

import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Chat.service.ChatService;
import com.loop.loop_backend.CompanionHeart.repository.CompanionHeartRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.ConcertScrap.repository.ConcertScrapRepository;
import com.loop.loop_backend.FavoriteArtist.repository.FavoriteArtistRepository;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.TicketAlarm.repository.TicketAlarmRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.dto.NotificationSettingsRequestDto;
import com.loop.loop_backend.User.dto.TermsAgreementRequestDto;
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
    @Mock CompanionPostRepository companionPostRepository;
    @Mock CompanionHeartRepository companionHeartRepository;
    @Mock ConcertScrapRepository concertScrapRepository;
    @Mock SetlistVoteRepository setlistVoteRepository;
    @Mock TicketAlarmRepository ticketAlarmRepository;
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

    // ── updateNotificationSettings ───────────────────────────────────────

    @Test
    void 셋리스트_결과_알림은_기본_ON이고_null이면_그대로_값이면_바뀐다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        assertThat(user.isSetlistResultEmail()).isTrue();

        NotificationSettingsRequestDto keep = new NotificationSettingsRequestDto();
        keep.setChatNotificationEmail(true);
        userService.updateNotificationSettings(USER_ID, keep);
        assertThat(user.isSetlistResultEmail()).isTrue();

        NotificationSettingsRequestDto off = new NotificationSettingsRequestDto();
        off.setSetlistResultEmail(false);
        userService.updateNotificationSettings(USER_ID, off);
        assertThat(user.isSetlistResultEmail()).isFalse();
        assertThat(user.isChatNotificationEmail()).isTrue();
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
    void 탈퇴_시점에_동행글_하트_해시태그_관심아티스트_콘서트스크랩_셋리스트투표_예매알림이_정리된다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.withdrawUser(USER_ID);

        verify(companionHeartRepository).deleteAllByCompanionPost_User(user);
        verify(companionHeartRepository).deleteAllByUser(user);
        verify(companionPostRepository).deleteAllByUser(user);
        verify(userHashtagRepository).deleteAllByUser(user);
        verify(favoriteArtistRepository).deleteAllByUser(user);
        verify(concertScrapRepository).deleteAllByUser(user);
        verify(setlistVoteRepository).deleteAllByUser(user);
        verify(ticketAlarmRepository).deleteAllByUser(user);
    }

    @Test
    void 존재하지_않는_사용자를_탈퇴처리하면_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdrawUser(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── isNicknameAvailable ──────────────────────────────────────────────

    @Test
    void 아무도_쓰지_않는_닉네임이면_사용_가능하다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByNickname("새닉네임")).thenReturn(false);

        assertThat(userService.isNicknameAvailable(USER_ID, "새닉네임")).isTrue();
    }

    @Test
    void 다른_사용자가_이미_쓰고_있는_닉네임이면_사용_불가능하다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.existsByNickname("이미있음")).thenReturn(true);

        assertThat(userService.isNicknameAvailable(USER_ID, "이미있음")).isFalse();
    }

    @Test
    void 본인이_이미_쓰고_있는_닉네임을_그대로_확인하면_사용_가능하다() {
        User user = testUser();
        user.updateUserProfile("내닉네임", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThat(userService.isNicknameAvailable(USER_ID, "내닉네임")).isTrue();
        // 본인 것과 같으면 중복 조회 자체를 할 필요가 없다
        verify(userRepository, org.mockito.Mockito.never()).existsByNickname(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 존재하지_않는_사용자로_닉네임을_확인하면_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.isNicknameAvailable(USER_ID, "아무닉네임"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── agreeToTerms ──────────────────────────────────────────────────────

    @Test
    void 필수_약관에_모두_동의하면_저장된다() {
        User user = testUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        userService.agreeToTerms(USER_ID, new TermsAgreementRequestDto(true, true, true, false));

        assertThat(user.isAgreementsCompleted()).isTrue();
        assertThat(user.isProfileInfoAgreed()).isFalse();
    }

    @Test
    void 나이_동의가_false면_AGREEMENT_REQUIRED_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser()));

        assertThatThrownBy(() -> userService.agreeToTerms(USER_ID, new TermsAgreementRequestDto(false, true, true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGREEMENT_REQUIRED);
    }

    @Test
    void 이용약관_동의가_false면_AGREEMENT_REQUIRED_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser()));

        assertThatThrownBy(() -> userService.agreeToTerms(USER_ID, new TermsAgreementRequestDto(true, false, true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGREEMENT_REQUIRED);
    }

    @Test
    void 개인정보_수집동의가_false면_AGREEMENT_REQUIRED_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser()));

        assertThatThrownBy(() -> userService.agreeToTerms(USER_ID, new TermsAgreementRequestDto(true, true, false, true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AGREEMENT_REQUIRED);
    }

    @Test
    void 존재하지_않는_사용자면_약관동의시_USER_NOT_FOUND_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.agreeToTerms(USER_ID, new TermsAgreementRequestDto(true, true, true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
