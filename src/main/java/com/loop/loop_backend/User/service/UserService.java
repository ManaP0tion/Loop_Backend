package com.loop.loop_backend.User.service;

import com.loop.loop_backend.Storage.dto.ImageUploadResponseDto;
import com.loop.loop_backend.User.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    //UserResponseDto registerEmail(UserRegisterRequestDto requestDto);
    UserResponseDto getUserById(Long id);
    UserResponseDto getUserByUserId(String userId);
    List<UserResponseDto> getAllUsers();
    UserResponseDto updateProfile(Long id, UserUpdateRequestDto requestDto);
    boolean isNicknameAvailable(Long id, String nickname);
    UserResponseDto completeOnboarding(Long id, OnboardingRequestDto requestDto);
    UserResponseDto agreeToTerms(Long id, TermsAgreementRequestDto requestDto);
    UserResponseDto updateNotificationSettings(Long id, NotificationSettingsRequestDto requestDto);
    void changePassword(Long id, PasswordChangeRequestDto requestDto);
    void withdrawUser(Long id);
    ImageUploadResponseDto uploadProfileImage(Long id, MultipartFile file);
}