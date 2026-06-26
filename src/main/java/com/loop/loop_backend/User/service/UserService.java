package com.loop.loop_backend.User.service;

import com.loop.loop_backend.User.dto.*;

import java.util.List;

public interface UserService {
    UserResponseDto registerEmail(UserRegisterRequestDto requestDto);
    UserResponseDto registerKakao(KakaoRegisterRequestDto requestDto);
    UserResponseDto getUserById(Long id);
    UserResponseDto getUserByUserId(String userId);
    List<UserResponseDto> getAllUsers();
    UserResponseDto updateUser(Long id, UserUpdateRequestDto requestDto);
    void deleteUser(Long id);
}
