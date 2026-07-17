package com.loop.loop_backend.User.controller;

import com.loop.loop_backend.Storage.dto.ImageUploadResponseDto;
import com.loop.loop_backend.User.dto.*;
import com.loop.loop_backend.User.service.UserService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "사용자 관리 API")
public class UserController {

    private final UserService userService;

//    @PostMapping("/register")
//    @Operation(summary = "이메일 회원가입", description = "이메일 계정으로 회원가입합니다")
//    @ApiResponses({
//            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
//            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 중복 아이디/이메일")
//    })
//    public ResponseEntity<CommonResponse<UserResponseDto>> registerEmail(
//            @Valid @RequestBody UserRegisterRequestDto requestDto) {
//        return ResponseEntity.status(HttpStatus.CREATED)
//                .body(CommonResponse.success(userService.registerEmail(requestDto)));
//    }


    @GetMapping("/me")
    @Operation(summary = "내 정보 조회", description = "Access Token으로 현재 로그인한 사용자 정보를 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
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
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> getUserById(
            @Parameter(description = "사용자 PK") @PathVariable Long id) {
        return ResponseEntity.ok(CommonResponse.success(userService.getUserById(id)));
    }

//    @GetMapping("/search")
//    @Operation(summary = "사용자 조회 (아이디)", description = "로그인 아이디로 이메일 사용자를 조회합니다")
//    @ApiResponses({
//            @ApiResponse(responseCode = "200", description = "조회 성공"),
//            @ApiResponse(responseCode = "404", description = "사용자 없음")
//    })
//    public ResponseEntity<CommonResponse<UserResponseDto>> getUserByUserId(
//            @Parameter(description = "로그인 아이디") @RequestParam String userId) {
//        return ResponseEntity.ok(CommonResponse.success(userService.getUserByUserId(userId)));
//    }

    @PutMapping("/me/profile")
    @Operation(summary = "프로필 수정", description = "로그인한 본인의 닉네임, 프로필 이미지를 수정합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserUpdateRequestDto requestDto) {
        return ResponseEntity.ok(CommonResponse.success(userService.updateProfile(userId, requestDto)));
    }

    @PostMapping("/me/profile-image")
    @Operation(summary = "프로필 이미지 업로드", description = "이미지를 공개 버킷에 업로드하고 URL을 반환합니다. " +
            "반환된 URL을 PUT /api/users/me/profile 요청의 profileImageUrl에 담아 보내야 실제 프로필에 반영됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(responseCode = "400", description = "허용되지 않는 파일 형식 또는 크기 초과",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"허용되지 않는 파일 형식입니다. (jpg, jpeg, png, webp만 가능)\",\"code\":400}")))
    })
    public ResponseEntity<CommonResponse<ImageUploadResponseDto>> uploadProfileImage(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(CommonResponse.success(userService.uploadProfileImage(userId, file)));
    }

    @PatchMapping("/me/onboarding")
    @Operation(summary = "온보딩", description = "로그인한 본인의 생년월일, 성별을 입력받아 온보딩을 완료합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "온보딩 완료"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}"))),
            @ApiResponse(responseCode = "409", description = "이미 온보딩을 완료",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"이미 온보딩을 완료했습니다.\",\"code\":409}")))
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> completeOnboarding(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody OnboardingRequestDto requestDto) {
        return ResponseEntity.ok(CommonResponse.success(userService.completeOnboarding(userId, requestDto)));
    }

    @PutMapping("/{id}/artists")
    @Operation(summary = "관심 아티스트 수정", description = "관심 아티스트 목록을 수정합니다 (구현 예정)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<UserResponseDto>> updateArtists(
            @Parameter(description = "사용자 PK") @PathVariable Long id,
            @Valid @RequestBody ArtistUpdateRequestDto requestDto) {
        return ResponseEntity.ok(CommonResponse.success(userService.updateArtists(id, requestDto)));
    }

//    @PatchMapping("/{id}/password")
//    @Operation(summary = "비밀번호 변경", description = "현재 비밀번호 확인 후 새 비밀번호로 변경합니다. 이메일 계정만 가능합니다.")
//    @ApiResponses({
//            @ApiResponse(responseCode = "204", description = "변경 성공"),
//            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 비밀번호 불일치"),
//            @ApiResponse(responseCode = "401", description = "현재 비밀번호 오류"),
//            @ApiResponse(responseCode = "404", description = "사용자 없음")
//    })
//    public ResponseEntity<Void> changePassword(
//            @Parameter(description = "사용자 PK") @PathVariable Long id,
//            @Valid @RequestBody PasswordChangeRequestDto requestDto) {
//        userService.changePassword(id, requestDto);
//        return ResponseEntity.noContent().build();
//    }

    @DeleteMapping("/{id}")
    @Operation(summary = "회원 탈퇴", description = "사용자 상태를 WITHDRAWN으로 변경합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 성공"),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<Void>> withdrawUser(
            @Parameter(description = "사용자 PK") @PathVariable Long id) {
        userService.withdrawUser(id);
        return ResponseEntity.ok(CommonResponse.success("탈퇴성공", null));
    }
}