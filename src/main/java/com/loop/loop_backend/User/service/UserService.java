package com.loop.loop_backend.User.service;

import com.loop.loop_backend.User.dto.*;

import java.util.List;

public interface UserService {
    //UserResponseDto registerEmail(UserRegisterRequestDto requestDto);
    UserResponseDto getUserById(Long id);
    UserResponseDto getUserByUserId(String userId);
    List<UserResponseDto> getAllUsers();
    UserResponseDto updateProfile(Long id, UserUpdateRequestDto requestDto);
    UserResponseDto completeOnboarding(Long id, OnboardingRequestDto requestDto);
    UserResponseDto updateArtists(Long id, ArtistUpdateRequestDto requestDto);
    UserResponseDto updateHashtags(Long id, HashtagUpdateRequestDto requestDto);
    void changePassword(Long id, PasswordChangeRequestDto requestDto);
    void withdrawUser(Long id);
}