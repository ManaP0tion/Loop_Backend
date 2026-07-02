package com.loop.loop_backend.User.service;

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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        return new UserResponseDto(findUserOrThrow(id));
    }

    @Override
    public UserResponseDto getUserByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new UserResponseDto(user);
    }

    @Override
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponseDto::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UserResponseDto updateProfile(Long id, UserUpdateRequestDto requestDto) {
        User user = findUserOrThrow(id);
        checkNicknameAvailable(user, requestDto.getNickname());
        user.updateUserProfile(requestDto.getNickname(), requestDto.getProfileImageUrl());
        return new UserResponseDto(user);
    }

    @Override
    @Transactional
    public UserResponseDto completeOnboarding(Long id, OnboardingRequestDto requestDto) {
        User user = findUserOrThrow(id);
        checkNicknameAvailable(user, requestDto.getNickname());
        user.completeOnboarding(requestDto.getNickname(), requestDto.getBirthDate(), requestDto.getGender());
        return new UserResponseDto(user);
    }

    private void checkNicknameAvailable(User user, String nickname) {
        if (!nickname.equals(user.getNickname()) && userRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    @Override
    @Transactional
    public UserResponseDto updateArtists(Long id, ArtistUpdateRequestDto requestDto) {
        // TODO: Artist 연관관계 연결 후 실제 저장 로직 구현
        User user = findUserOrThrow(id);
        return new UserResponseDto(user);
    }

    @Override
    @Transactional
    public UserResponseDto updateHashtags(Long id, HashtagUpdateRequestDto requestDto) {
        // TODO: Hashtag 연관관계 연결 후 실제 저장 로직 구현
        User user = findUserOrThrow(id);
        return new UserResponseDto(user);
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
}