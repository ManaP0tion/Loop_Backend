package com.loop.loop_backend.CompanionPost.dto;

import com.loop.loop_backend.CompanionPost.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

@Getter
@NoArgsConstructor
@Schema(description = "동행프로필 생성 요청 DTO")
public class CompanionRequestDto {

    @NotNull(message = "콘서트 ID는 필수입니다")
    @Schema(description = "콘서트 PK", example = "1")
    private Long concertId;

    @NotNull(message = "관람 일차는 필수입니다")
    @Schema(description = "관람 일차", example = "DAY1")
    private WatchDay watchDay;

    @NotNull(message = "함께 하고 싶은 활동은 필수입니다")
    @Schema(description = "함께 하고 싶은 활동 목록", example = "[\"MEAL\", \"PHOTO\"]")
    private Set<CompanionActivity> activities;

    @Schema(description = "관람 스타일 (함께하고 싶은 것에 공연 관람을 선택했을 때만 의미 있음)", example = "NORMAL")
    private WatchStyle watchStyle;

    @Schema(description = "동행에게 남기는 메시지", example = "같이 즐겁게 봐요!")
    private String messageToCompanion;

    @Schema(description = "같은 성별에게만 연락받기", example = "false")
    private boolean sameGenderOnly;
}
