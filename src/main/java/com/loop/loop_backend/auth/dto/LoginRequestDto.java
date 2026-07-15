package com.loop.loop_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequestDto {

    @NotBlank(message = "아이디는 필수입니다")
    private String userId;

    @NotBlank(message = "비밀번호는 필수입니다")
    private String password;
}