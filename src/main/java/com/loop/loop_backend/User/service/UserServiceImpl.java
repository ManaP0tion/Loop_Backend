package com.loop.loop_backend.User.service;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.User.dto.*;
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

    @Override
    @Transactional
    public UserResponseDto registerEmail(UserRegisterRequestDto requestDto) {
        if (userRepository.existsByUserId(requestDto.getUserId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다: " + requestDto.getUserId());
        }
        if (requestDto.getEmail() != null && userRepository.existsByEmail(requestDto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다: " + requestDto.getEmail());
        }

        User user = new User();
        user.setAuthProvider(AuthProvider.EMAIL);
        user.setUserId(requestDto.getUserId());
        user.setPassword(passwordEncoder.encode(requestDto.getPassword()));
        user.setEmail(requestDto.getEmail());
        user.setNickname(requestDto.getNickname());
        user.setGender(requestDto.getGender());
        user.setAgeGroup(requestDto.getAgeGroup());
        user.setOnboardingCompleted(true);

        return new UserResponseDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponseDto registerKakao(KakaoRegisterRequestDto requestDto) {
        if (userRepository.existsByAuthProviderAndProviderId(AuthProvider.KAKAO, requestDto.getProviderId())) {
            throw new IllegalArgumentException("이미 가입된 카카오 계정입니다.");
        }
        if (requestDto.getEmail() != null && userRepository.existsByEmail(requestDto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다: " + requestDto.getEmail());
        }

        User user = new User();
        user.setAuthProvider(AuthProvider.KAKAO);
        user.setProviderId(requestDto.getProviderId());
        user.setEmail(requestDto.getEmail());
        user.setNickname(requestDto.getNickname());
        user.setGender(requestDto.getGender());
        user.setAgeGroup(requestDto.getAgeGroup());
        user.setOnboardingCompleted(true);

        return new UserResponseDto(userRepository.save(user));
    }

    @Override
    public UserResponseDto getUserById(Long id) {
        return new UserResponseDto(findUserOrThrow(id));
    }

    @Override
    public UserResponseDto getUserByUserId(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. userId: " + userId));
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
    public UserResponseDto updateUser(Long id, UserUpdateRequestDto requestDto) {
        User user = findUserOrThrow(id);

        user.setNickname(requestDto.getNickname());
        user.setEmail(requestDto.getEmail());
        user.setGender(requestDto.getGender());
        user.setAgeGroup(requestDto.getAgeGroup());

        if (user.getAuthProvider() == AuthProvider.EMAIL
                && requestDto.getPassword() != null
                && !requestDto.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(requestDto.getPassword()));
        }

        return new UserResponseDto(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = findUserOrThrow(id);
        userRepository.delete(user);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. id: " + id));
    }
}
