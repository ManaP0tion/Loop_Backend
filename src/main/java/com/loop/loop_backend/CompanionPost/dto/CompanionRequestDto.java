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

    @Schema(description = "선호하는 동행자 성별", example = "ANY")
    private PreferredGender preferredGender;

    @NotNull(message = "선호하는 동행자 나이대는 필수입니다")
    @Schema(description = "선호하는 동행자 나이대 목록", example = "[\"TWENTY_FIVE_TO_TWENTY_NINE\", \"THIRTY_TO_THIRTY_FOUR\"]")
    private Set<PreferredAgeGroup> preferredAgeGroups;

    @NotNull(message = "함께 하고 싶은 활동은 필수입니다")
    @Schema(description = "함께 하고 싶은 활동 목록", example = "[\"MEAL\", \"PHOTO\"]")
    private Set<CompanionActivity> activities;

    @NotNull(message = "관람 스타일은 필수입니다")
    @Schema(description = "관람 스타일", example = "NORMAL")
    private WatchStyle watchStyle;

    @Schema(description = "동행에게 남기는 메시지", example = "같이 즐겁게 봐요!")
    private String messageToCompanion;
}
