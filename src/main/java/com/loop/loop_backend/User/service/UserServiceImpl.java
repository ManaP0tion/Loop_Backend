package com.loop.loop_backend.User.service;

import com.loop.loop_backend.FavoriteArtist.repository.FavoriteArtistRepository;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.Storage.dto.ImageUploadResponseDto;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.User.dto.*;
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
    private final PasswordEncoder passwordEncoder;
    private final S3StorageService s3StorageService;

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

    private void checkNicknameAvailable(User user, String nickname) {
        if (!nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
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
        user.withdraw();
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