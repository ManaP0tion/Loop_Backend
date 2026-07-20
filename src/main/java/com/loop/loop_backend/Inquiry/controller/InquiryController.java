package com.loop.loop_backend.Inquiry.controller;

import com.loop.loop_backend.Inquiry.dto.InquiryRequestDto;
import com.loop.loop_backend.Inquiry.service.InquiryService;
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
@RequestMapping("/api/users/me/inquiries")
@RequiredArgsConstructor
@Tag(name = "Inquiry", description = "고객센터 문의 API")
public class InquiryController {

    private final InquiryService inquiryService;

    @PostMapping
    @Operation(summary = "고객센터 문의 등록", description = "문의 유형, 제목, 내용을 입력받아 문의를 접수합니다. " +
            "답변받을 이메일은 온보딩 시 입력한 계정 이메일을 사용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "접수 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"입력값이 올바르지 않습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}")))
    })
    public ResponseEntity<CommonResponse<Void>> submitInquiry(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InquiryRequestDto requestDto) {
        inquiryService.submitInquiry(userId, requestDto);
        return ResponseEntity.ok(CommonResponse.success("문의가 접수되었습니다.", null));
    }
}