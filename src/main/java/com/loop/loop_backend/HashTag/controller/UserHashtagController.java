package com.loop.loop_backend.HashTag.controller;

import com.loop.loop_backend.HashTag.dto.HashtagCreateRequestDto;
import com.loop.loop_backend.HashTag.dto.HashtagReplaceRequestDto;
import com.loop.loop_backend.HashTag.dto.HashtagResponseDto;
import com.loop.loop_backend.HashTag.service.UserHashtagService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Hashtag", description = "관심 해시태그 API")
public class UserHashtagController {

    private final UserHashtagService userHashtagService;

//    @GetMapping("/me/hashtags")
//    @Operation(summary = "관심 해시태그 조회", description = "내 관심 해시태그 목록을 조회합니다")
//    @ApiResponse(responseCode = "200", description = "조회 성공")
//    public ResponseEntity<CommonResponse<List<HashtagResponseDto>>> getHashtags(
//            @AuthenticationPrincipal Long userId) {
//        return ResponseEntity.ok(CommonResponse.success(userHashtagService.getHashtags(userId)));
//    }

    @GetMapping("/{userId}/hashtags")
    @Operation(summary = "사용자 해시태그 조회", description = "사용자 PK로 관심 해시태그 목록을 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}"))),
            @ApiResponse(responseCode = "410", description = "탈퇴한 사용자",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"탈퇴한 사용자입니다.\",\"code\":410}")))
    })
    public ResponseEntity<CommonResponse<List<HashtagResponseDto>>> getHashtagsByUserId(
            @Parameter(description = "사용자 PK") @PathVariable Long userId) {
        return ResponseEntity.ok(CommonResponse.success(userHashtagService.getHashtags(userId)));
    }

    @PutMapping("/me/hashtags")
    @Operation(summary = "관심 해시태그 목록 교체",
            description = "보낸 목록으로 내 해시태그를 통째로 교체합니다 (최대 5개). 빠진 태그는 삭제되고 []는 모두 비웁니다.\n\n" +
                    "교체할 때마다 해시태그 id가 새로 발급되므로 응답의 id를 사용하세요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "교체 성공 - 저장된 목록"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 (태그 1~5자, 특수문자 불가)",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "409", description = "목록에 같은 태그가 있거나 개수 초과",
                    content = @Content(examples = {
                            @ExampleObject(name = "중복 태그",
                                    value = "{\"success\":false,\"message\":\"이미 사용중인 해시태그입니다.\",\"code\":409}"),
                            @ExampleObject(name = "개수 초과",
                                    value = "{\"success\":false,\"message\":\"해시태그는 최대 5개까지 등록할 수 있습니다.\",\"code\":409}")
                    }))
    })
    public ResponseEntity<CommonResponse<List<HashtagResponseDto>>> replaceHashtags(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody HashtagReplaceRequestDto requestDto) {
        return ResponseEntity.ok(CommonResponse.success(
                userHashtagService.replaceHashtags(userId, requestDto.getTags())));
    }

    @PostMapping("/me/hashtags")
    @Operation(summary = "관심 해시태그 추가", description = "내 관심 해시태그를 추가합니다 (최대 5개)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "추가 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "409", description = "이미 등록된 해시태그이거나 개수 초과",
                    content = @Content(examples = {
                            @ExampleObject(name = "중복 태그",
                                    value = "{\"success\":false,\"message\":\"이미 사용중인 해시태그입니다.\",\"code\":409}"),
                            @ExampleObject(name = "개수 초과",
                                    value = "{\"success\":false,\"message\":\"해시태그는 최대 5개까지 등록할 수 있습니다.\",\"code\":409}")
                    }))
    })
    public ResponseEntity<CommonResponse<HashtagResponseDto>> addHashtag(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody HashtagCreateRequestDto requestDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(userHashtagService.addHashtag(userId, requestDto.getTag())));
    }

    @DeleteMapping("/me/hashtags/{hashtagId}")
    @Operation(summary = "관심 해시태그 삭제", description = "내 관심 해시태그를 삭제합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "404", description = "해시태그를 찾을 수 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"해시태그를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<Void>> deleteHashtag(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long hashtagId) {
        userHashtagService.deleteHashtag(userId, hashtagId);
        return ResponseEntity.ok(CommonResponse.success("해시태그 삭제성공",null));
    }
}