package com.loop.loop_backend.Lineup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 라인업 추가. 두 방식 중 하나만 보낸다.
 * - DB 선택: artistIds (아티스트 관리에 등록된 아티스트 여러 명)
 * - 직접 입력: name (+ imageUrl) - 아티스트 DB에도 새로 저장된다
 */
@Schema(description = "라인업 추가 요청. artistIds(DB 선택)와 name(직접 입력) 중 하나만 보낸다")
public record LineupAddRequest(

        @NotNull
        @Schema(description = "DAY 번호(1부터). 하루 페스티벌이면 1", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer day,

        @Schema(description = "DB 선택: 아티스트 PK 목록. 순서대로 라인업 맨 뒤에 붙는다", example = "[3, 7]",
                types = {"array", "null"})
        List<Long> artistIds,

        @Size(max = 100)
        @Schema(description = "직접 입력: 아티스트 이름", example = "Vaundy", types = {"string", "null"})
        String name,

        @Size(max = 500)
        @Schema(description = "직접 입력: 아티스트 이미지 URL(이미지 업로드 API로 받은 값). 없으면 null", types = {"string", "null"})
        String imageUrl
) {
}
