package com.loop.loop_backend.User.controller;

import com.loop.loop_backend.User.dto.*;
import com.loop.loop_backend.User.service.UserService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 관리 API")
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "이메일 회원가입", description = "이메일 계정으로 회원가입합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 중복 아이디/이메일")
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> registerEmail(
            @Valid @RequestBody UserRegisterRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(userService.registerEmail(requestDto)));
    }

    @PostMapping("/kakao")
    @Operation(summary = "카카오 회원가입", description = "카카오 OAuth로 회원가입합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 중복 계정")
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> registerKakao(
            @Valid @RequestBody KakaoRegisterRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(userService.registerKakao(requestDto)));
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "Access Token으로 현재 로그인한 사용자 정보를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> getMe(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(userService.getUserById(userId)));
    }

    @GetMapping
    @Operation(summary = "전체 사용자 조회", description = "모든 사용자 목록을 반환합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    public ResponseEntity<CommonResponse<List<UserResponseDto>>> getAllUsers() {
        return ResponseEntity.ok(CommonResponse.success(userService.getAllUsers()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "사용자 조회 (PK)", description = "PK로 사용자를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> getUserById(
            @Parameter(description = "사용자 PK") @PathVariable Long id) {
        return ResponseEntity.ok(CommonResponse.success(userService.getUserById(id)));
    }

    @GetMapping("/search")
    @Operation(summary = "사용자 조회 (아이디)", description = "로그인 아이디로 이메일 사용자를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> getUserByUserId(
            @Parameter(description = "로그인 아이디") @RequestParam String userId) {
        return ResponseEntity.ok(CommonResponse.success(userService.getUserByUserId(userId)));
    }

    @PutMapping("/{id}/profile")
    @Operation(summary = "프로필 수정", description = "닉네임, 이메일, 성별, 연령대를 수정합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> updateProfile(
            @Parameter(description = "사용자 PK") @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequestDto requestDto) {
        return ResponseEntity.ok(CommonResponse.success(userService.updateProfile(id, requestDto)));
    }

    @PatchMapping("/{id}/password")
    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호 확인 후 새 비밀번호로 변경합니다. 이메일 계정만 가능합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 비밀번호 불일치"),
            @ApiResponse(responseCode = "401", description = "현재 비밀번호 오류"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<Void> changePassword(
            @Parameter(description = "사용자 PK") @PathVariable Long id,
            @Valid @RequestBody PasswordChangeRequestDto requestDto) {
        userService.changePassword(id, requestDto);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "회원 탈퇴", description = "사용자 상태를 WITHDRAWN으로 변경합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "탈퇴 성공"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<Void> withdrawUser(
            @Parameter(description = "사용자 PK") @PathVariable Long id) {
        userService.withdrawUser(id);
        return ResponseEntity.noContent().build();
    }
}