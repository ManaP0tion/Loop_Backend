package com.loop.loop_backend.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "페이지네이션 목록 응답 DTO")
public class PageResponseDto<T> {

    @Schema(description = "목록")
    private final List<T> content;

    @Schema(description = "조건에 맞는 전체 개수", example = "12")
    private final long totalElements;

    @Schema(description = "다음 페이지 존재 여부", example = "true")
    private final boolean hasNext;

    public PageResponseDto(List<T> content, long totalElements, boolean hasNext) {
        this.content = content;
        this.totalElements = totalElements;
        this.hasNext = hasNext;
    }
}