package com.loop.loop_backend.User.controller;

import com.loop.loop_backend.User.dto.EmailVerificationConfirmRequestDto;
import com.loop.loop_backend.User.dto.EmailVerificationRequestDto;
import com.loop.loop_backend.User.service.EmailVerificationService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/email")
@RequiredArgsConstructor
@Tag(name = "EmailVerification", description = "이메일 인증 API")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @PostMapping("/verification-code")
    @Operation(summary = "이메일 인증 코드 발송", description = "입력한 이메일로 6자리 인증 코드를 발송합니다 (유효시간 3분). " +
            "재전송도 동일 API를 사용하며, 재요청 시 이전 코드는 무효화됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "발송 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"이메일 형식이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "429", description = "발송 요청 횟수 초과 (시간당 5회)",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"이메일 인증 코드 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\",\"code\":429}"))),
            @ApiResponse(responseCode = "500", description = "이메일 발송 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"이메일 발송에 실패했습니다.\",\"code\":500}")))
    })
    public ResponseEntity<CommonResponse<Void>> sendVerificationCode(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody EmailVerificationRequestDto requestDto) {
        emailVerificationService.sendCode(userId, requestDto.getEmail());
        return ResponseEntity.ok(CommonResponse.success("인증 코드가 발송되었습니다.", null));
    }

    @PostMapping("/verify")
    @Operation(summary = "이메일 인증 코드 확인", description = "발송된 인증 코드를 확인합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "인증 코드 불일치 또는 만료",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"인증 코드가 올바르지 않거나 만료되었습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "409", description = "이미 다른 계정에서 사용 중인 이메일",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"이미 사용 중인 이메일입니다.\",\"code\":409}")))
    })
    public ResponseEntity<CommonResponse<Void>> verifyCode(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody EmailVerificationConfirmRequestDto requestDto) {
        emailVerificationService.verifyCode(userId, requestDto.getEmail(), requestDto.getCode());
        return ResponseEntity.ok(CommonResponse.success("이메일 인증이 완료되었습니다.", null));
    }
}