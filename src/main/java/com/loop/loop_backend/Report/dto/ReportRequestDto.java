package com.loop.loop_backend.Report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "신고 요청 DTO")
public class ReportRequestDto {

    @NotNull(message = "신고할 사용자 ID는 필수입니다")
    @Schema(description = "신고할 사용자 PK", example = "2")
    private Long targetUserId;

    @Schema(description = "신고 사유", example = "부적절한 프로필 사진")
    private String reason;

    @Schema(description = "상세 설명", example = "채팅에서 지속적으로 욕설을 사용했습니다.")
    private String detail;

    @Schema(description = "신고와 함께 차단할지 여부", example = "true")
    private boolean blockToo;

    @Schema(description = "신고 증빙 이미지 URL 목록 (업로드 자체는 별도 API로 대체 예정)")
    private List<String> imageUrls;
}