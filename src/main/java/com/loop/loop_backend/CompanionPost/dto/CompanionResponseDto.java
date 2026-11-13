package com.loop.loop_backend.CompanionPost.dto;

import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.domain.WatchStyle;
import com.loop.loop_backend.HashTag.domain.UserHashtag;
import com.loop.loop_backend.User.domain.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Schema(description = "동행 프로필 응답 DTO")
public class CompanionResponseDto {

    @Schema(description = "동행 프로필 PK", example = "1")
    private final Long companionId;

    @Schema(description = "작성자 PK", example = "10")
    private final Long userId;

    @Schema(description = "작성자 닉네임", example = "루퍼123")
    private final String nickname;

    @Schema(description = "작성자 프로필 이미지 URL")
    private final String profileImageUrl;

    @Schema(description = "작성자 성별", example = "MALE")
    private final Gender gender;

    @Schema(description = "작성자 나이 (생년월일 없으면 null)", example = "27")
    private final Integer age;

    @Schema(description = "콘서트 PK", example = "1")
    private final Long concertId;

    @Schema(description = "관람 일차", example = "DAY1")
    private final WatchDay watchDay;

    @Schema(description = "함께 하고 싶은 활동 목록 (v2 목록 카드에서는 쓰지 않고 상세에서만 보여준다)")
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

    @Schema(description = "내가 이 프로필에 하트를 눌렀는지 여부", example = "false")
    private final boolean hearted;

    // 성격은 동행 프로필이 아니라 작성자(사용자)에 붙어 있다 - 같은 사람의 프로필은 모두 같은 성격
    @Schema(description = "작성자 성격(해시태그) 목록. 저장한 순서, 없으면 []")
    private final List<HashtagSummary> hashtags;

    public CompanionResponseDto(CompanionPost post, boolean hearted, List<HashtagSummary> hashtags) {
        this.companionId = post.getId();
        this.userId = post.getUser().getId();
        this.nickname = post.getUser().getNickname();
        this.profileImageUrl = post.getUser().getProfileImageUrl();
        this.gender = post.getUser().getGender();
        this.age = post.getUser().getAge();
        this.concertId = post.getConcert().getId();
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
        this.hearted = hearted;
        this.hashtags = hashtags;
    }

    @Getter
    @Schema(description = "해시태그(성격) 요약 정보")
    public static class HashtagSummary {

        @Schema(description = "해시태그 ID", example = "1")
        private final Long id;

        @Schema(description = "해시태그", example = "낯가림")
        private final String tag;

        public HashtagSummary(Long id, String tag) {
            this.id = id;
            this.tag = tag;
        }

        /** 여러 작성자의 성격을 작성자별로 묶는다. 넘겨받은 순서(저장 순서)를 유지한다. */
        public static Map<Long, List<HashtagSummary>> groupByUser(List<UserHashtag> tags) {
            return tags.stream().collect(Collectors.groupingBy(tag -> tag.getUser().getId(),
                    Collectors.mapping(tag -> new HashtagSummary(tag.getId(), tag.getTag()), Collectors.toList())));
        }
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