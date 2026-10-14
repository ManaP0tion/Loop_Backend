package com.loop.loop_backend.Venue.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 공연장 CSV 업로드 결과. 행 번호는 헤더를 빼고 1부터 센다(엑셀에서는 +1한 줄). */
@Schema(description = "공연장 CSV 업로드 결과")
public record VenueCsvImportResult(

        @Schema(description = "값이 바뀐 공연장 수", requiredMode = Schema.RequiredMode.REQUIRED)
        int updated,

        @Schema(description = "바뀐 값이 없는 행 수(고칠 칸이 모두 비었거나 기존 값과 같음)", requiredMode = Schema.RequiredMode.REQUIRED)
        int unchanged,

        @Schema(description = "반영하지 못한 행 번호(헤더 제외 1부터). id가 비었거나 없는 공연장, 잘못된 값(링크 형식·수용 인원·길이)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<Long> failedRows
) {
}