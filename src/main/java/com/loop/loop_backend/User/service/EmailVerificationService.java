package com.loop.loop_backend.User.service;

public interface EmailVerificationService {

    void sendCode(Long userId, String email);
    void verifyCode(Long userId, String email, String code);
}