package com.loop.loop_backend.Test.controller;

import com.loop.loop_backend.Test.dto.TestUserResponseDto;
import com.loop.loop_backend.Test.service.TestUserService;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.auth.service.RefreshTokenService;
import com.loop.loop_backend.common.exception.CommonResponse;
import com.loop.loop_backend.common.jwt.JwtTokenProvider;
import com.loop.loop_backend.common.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Tag(name = "Test", description = "스웨거 테스트 전용 API (운영 배포 시 제거 예정)")
public class TestUserController {

    private final TestUserService testUserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;

    @PostMapping("/users")
    @Operation(summary = "테스트 사용자 생성 + 로그인",
            description = "온보딩이 완료된 테스트 사용자를 즉시 생성하고 로그인 처리합니다. " +
                    "refreshToken은 쿠키로 내려주고, accessToken은 응답 바디로 반환하니 Swagger 우측 상단 Authorize에 " +
                    "\"Bearer {accessToken}\" 형태로 입력한 뒤 다른 API를 바로 테스트하면 됩니다. 닉네임 미입력 시 랜덤 생성됩니다.")
    public ResponseEntity<CommonResponse<TestUserResponseDto>> createTestUser(
            @Parameter(description = "테스트 사용자 닉네임 (5자 이하, 미입력 시 랜덤 생성)")
            @RequestParam(required = false) String nickname) {

        User user = testUserService.createTestUser(nickname);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.createRefreshTokenCookie(refreshToken).toString())
                .body(CommonResponse.success("테스트 사용자가 생성되었습니다.", new TestUserResponseDto(user, accessToken)));
    }
}