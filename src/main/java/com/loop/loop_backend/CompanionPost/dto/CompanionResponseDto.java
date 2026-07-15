package com.loop.loop_backend.CompanionPost.dto;

import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.domain.WatchStyle;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Schema(description = "동행 프로필 응답 DTO")
public class CompanionResponseDto {

    @Schema(description = "동행 프로필 PK", example = "1")
    private final Long companionId;

    @Schema(description = "작성자 PK", example = "10")
    private final Long userId;

    @Schema(description = "작성자 닉네임", example = "루퍼123")
    private final String nickname;

    @Schema(description = "콘서트 PK", example = "1")
    private final Long concertId;

    @Schema(description = "관람 일차", example = "DAY1")
    private final WatchDay watchDay;

    @Schema(description = "함께 하고 싶은 활동 목록")
    private final List<EnumLabelDto> activities;

    @Schema(description = "관람 스타일")
    private final EnumLabelDto watchStyle;

    @Schema(description = "동행에게 남기는 메시지", example = "같이 즐겁게 봐요!")
    private final String messageToCompanion;

    @Schema(description = "공개 여부", example = "true")
    private final boolean visible;

    @Schema(description = "같은 성별에게만 연락받기", example = "false")
    private final boolean sameGenderOnly;

    @Schema(description = "생성일시")
    private final LocalDateTime createdAt;

    public CompanionResponseDto(CompanionPost post) {
        this.companionId = post.getId();
        this.userId = post.getUser().getId();
        this.nickname = post.getUser().getNickname();
        this.concertId = post.getConcertId();
        this.watchDay = post.getWatchDay();
        this.activities = post.getActivities().stream()
                .map(activity -> new EnumLabelDto(activity.name(), activity.getLabel()))
                .toList();
        this.watchStyle = post.getWatchStyle() != null
                ? new EnumLabelDto(post.getWatchStyle().name(), post.getWatchStyle().getLabel())
                : null;
        this.messageToCompanion = post.getMessageToCompanion();
        this.visible = post.isVisible();
        this.sameGenderOnly = post.isSameGenderOnly();
        this.createdAt = post.getCreatedAt();
    }

    @Getter
    @Schema(description = "enum 값과 라벨")
    public static class EnumLabelDto {

        @Schema(description = "enum 값", example = "enum값")
        private final String value;

        @Schema(description = "라벨", example = "한글 라벨 매핑")
        private final String label;

        public EnumLabelDto(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}