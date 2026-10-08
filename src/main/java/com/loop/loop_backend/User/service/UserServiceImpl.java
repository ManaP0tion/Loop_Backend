package com.loop.loop_backend.User.service;

import com.loop.loop_backend.Chat.service.ChatService;
import com.loop.loop_backend.CompanionHeart.repository.CompanionHeartRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.ConcertScrap.repository.ConcertScrapRepository;
import com.loop.loop_backend.FavoriteArtist.repository.FavoriteArtistRepository;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import com.loop.loop_backend.Storage.dto.ImageUploadResponseDto;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.User.dto.*;
import com.loop.loop_backend.auth.service.RefreshTokenService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserHashtagRepository userHashtagRepository;
    private final FavoriteArtistRepository favoriteArtistRepository;
    private final CompanionPostRepository companionPostRepository;
    private final CompanionHeartRepository companionHeartRepository;
    private final ConcertScrapRepository concertScrapRepository;
    private final SetlistVoteRepository setlistVoteRepository;
    private final PasswordEncoder passwordEncoder;
    private final S3StorageService s3StorageService;
    private final ChatService chatService;
    private final RefreshTokenService refreshTokenService;

//    @Override
//    @Transactional
//    public UserResponseDto registerEmail(UserRegisterRequestDto requestDto) {
//        if (userRepository.existsByUserId(requestDto.getUserId())) {
//            throw new BusinessException(ErrorCode.DUPLICATE_USER_ID);
//        }
//        if (requestDto.getEmail() != null && userRepository.existsByEmail(requestDto.getEmail())) {
//            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
//        }
//
//        User user = User.builder()
//                .authProvider(AuthProvider.EMAIL)
//                .status(Status.ACTIVE)
//                .onboardingCompleted(true)
//                .build();
//
//        return new UserResponseDto(userRepository.save(user));
//    }


    @Override
    public UserResponseDto getUserById(Long id) {
        return toResponseDto(findUserOrThrow(id));
    }

    @Override
    public UserResponseDto getUserByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toResponseDto(user);
    }

    @Override
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponseDto updateProfile(Long id, UserUpdateRequestDto requestDto) {
        User user = findUserOrThrow(id);
        checkNicknameAvailable(user, requestDto.getNickname());
        user.updateUserProfile(requestDto.getNickname(), requestDto.getProfileImageUrl());
        return toResponseDto(user);
    }

    @Override
    @Transactional
    public UserResponseDto completeOnboarding(Long id, OnboardingRequestDto requestDto) {
        User user = findUserOrThrow(id);

        if (user.isOnboardingCompleted()) {
            throw new BusinessException(ErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }

        checkNicknameAvailable(user, requestDto.getNickname());
        user.completeOnboarding(requestDto.getNickname(), requestDto.getBirthDate(), requestDto.getGender());
        return toResponseDto(user);
    }

    @Override
    @Transactional
    public UserResponseDto agreeToTerms(Long id, TermsAgreementRequestDto requestDto) {
        User user = findUserOrThrow(id);

        if (!requestDto.isAge19Agreed() || !requestDto.isTermsAgreed() || !requestDto.isPrivacyAgreed()) {
            throw new BusinessException(ErrorCode.AGREEMENT_REQUIRED);
        }

        user.agreeToTerms(requestDto.isAge19Agreed(), requestDto.isTermsAgreed(),
                requestDto.isPrivacyAgreed(), requestDto.isProfileInfoAgreed());
        return toResponseDto(user);
    }

    private void checkNicknameAvailable(User user, String nickname) {
        if (isNicknameTaken(user, nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Override
    public boolean isNicknameAvailable(Long id, String nickname) {
        return !isNicknameTaken(findUserOrThrow(id), nickname);
    }

    // 본인이 이미 쓰고 있는 닉네임은 "중복"으로 치지 않는다 (수정 시 그대로 두는 경우 포함)
    private boolean isNicknameTaken(User user, String nickname) {
        return !nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname);
    }

    @Override
    @Transactional
    public UserResponseDto updateNotificationSettings(Long id, NotificationSettingsRequestDto requestDto) {
        User user = findUserOrThrow(id);
        user.updateNotificationSettings(requestDto.getConcertReminderEmail(), requestDto.getChatNotificationEmail(),
                requestDto.getSetlistResultEmail());
        return toResponseDto(user);
    }

    private UserResponseDto toResponseDto(User user) {
        List<UserResponseDto.HashtagSummary> hashtags = userHashtagRepository.findAllByUser(user).stream()
                .map(tag -> new UserResponseDto.HashtagSummary(tag.getId(), tag.getTag()))
                .toList();
        List<UserResponseDto.ArtistSummary> favoriteArtists = favoriteArtistRepository.findAllByUser(user).stream()
                .map(fa -> new UserResponseDto.ArtistSummary(
                        fa.getId(), fa.getArtist().getId(), fa.getArtist().getName(), fa.getArtist().getImageUrl()))
                .toList();
        return new UserResponseDto(user, hashtags, favoriteArtists);
    }


    @Override
    @Transactional
    public void changePassword(Long id, PasswordChangeRequestDto requestDto) {
        User user = findUserOrThrow(id);

        if (user.getAuthProvider() != AuthProvider.EMAIL) {
            throw new BusinessException(ErrorCode.SOCIAL_LOGIN_NO_PASSWORD);
        }
        if (!passwordEncoder.matches(requestDto.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD);
        }
        if (!requestDto.getNewPassword().equals(requestDto.getNewPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        if (passwordEncoder.matches(requestDto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(requestDto.getNewPassword()));
    }

    @Override
    @Transactional
    public void withdrawUser(Long id) {
        User user = findUserOrThrow(id);
        chatService.handleUserWithdrawn(id);

        // 탈퇴 시점에 잔존 데이터를 정리한다 (재가입 여부와 무관하게 즉시 정리).
        // 삭제 순서 주의: 내 글을 참조하는 하트부터 지운 뒤에 글을 지워야 FK 위반이 안 남
        // 차단(Block)은 재가입해도 유지되어야 하므로 여기서 지우지 않는다
        companionHeartRepository.deleteAllByCompanionPost_User(user);
        companionHeartRepository.deleteAllByUser(user);
        companionPostRepository.deleteAllByUser(user);
        userHashtagRepository.deleteAllByUser(user);
        favoriteArtistRepository.deleteAllByUser(user);
        concertScrapRepository.deleteAllByUser(user);
        setlistVoteRepository.deleteAllByUser(user);

        user.withdraw();
        refreshTokenService.delete(id);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public ImageUploadResponseDto uploadProfileImage(Long id, MultipartFile file) {
        findUserOrThrow(id);
        String url = s3StorageService.uploadPublic("profiles", id, file);
        return new ImageUploadResponseDto(url);
    }
}