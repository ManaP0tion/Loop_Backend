package com.loop.loop_backend.Report.controller;

import com.loop.loop_backend.Report.dto.ReportRequestDto;
import com.loop.loop_backend.Report.service.ReportService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users/me/reports")
@RequiredArgsConstructor
@Tag(name = "Report", description = "사용자 신고 API")
public class ReportController {

    private final ReportService reportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "사용자 신고", description = "특정 사용자를 신고합니다. blockToo=true면 신고와 함께 차단합니다. " +
            "증빙 이미지(images)는 선택값이며, 여러 장 첨부할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "신고 성공"),
            @ApiResponse(responseCode = "400", description = "자기 자신을 신고하려는 경우",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"자기 자신을 신고/차단할 수 없습니다.\",\"code\":400}"))),
            @ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"사용자를 찾을 수 없습니다.\",\"code\":404}"))),
            @ApiResponse(responseCode = "409", description = "이미 신고한 사용자",
                    content = @Content(examples = @ExampleObject(
                            value = "{\"success\":false,\"message\":\"이미 신고한 사용자입니다.\",\"code\":409}")))
    })
    public ResponseEntity<CommonResponse<Void>> report(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestPart("request") ReportRequestDto requestDto,
            @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        reportService.report(userId, requestDto.getTargetUserId(), requestDto.getReason(), requestDto.getDetail(),
                requestDto.isBlockToo(), images);
        return ResponseEntity.ok(CommonResponse.success("신고가 접수되었습니다.", null));
    }
}