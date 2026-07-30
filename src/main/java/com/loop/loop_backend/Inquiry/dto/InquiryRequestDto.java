package com.loop.loop_backend.Inquiry.dto;

import com.loop.loop_backend.Inquiry.domain.InquiryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "고객센터 문의 요청 DTO")
public class InquiryRequestDto {

    @NotNull(message = "문의 유형은 필수입니다")
    @Schema(description = "문의 유형", example = "OTHER")
    private InquiryType type;

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자 이하여야 합니다")
    @Schema(description = "제목", example = "동행 목록이 안떠요")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    @Schema(description = "내용", example = "동행탭을 누르면 목록이 안떠요")
    private String content;
}