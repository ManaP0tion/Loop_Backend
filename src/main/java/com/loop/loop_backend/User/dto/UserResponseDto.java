package com.loop.loop_backend.User.dto;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Schema(description = "사용자 응답 DTO")
public class UserResponseDto {

    @Schema(description = "사용자 PK", example = "1")
    private final Long id;

    @Schema(description = "가입 경로", example = "EMAIL")
    private final AuthProvider authProvider;

    @Schema(description = "로그인 아이디 (이메일 계정)", example = "user123")
    private final String userId;

    @Schema(description = "이메일", example = "user@example.com")
    private final String email;

    @Schema(description = "닉네임", example = "루퍼123")
    private final String nickname;

    @Schema(description = "프로필 이미지 URL")
    private final String profileImageUrl;

    @Schema(description = "성별", example = "MALE")
    private final Gender gender;

    @Schema(description = "생년월일", example = "2000-01-01")
    private final LocalDate birthDate;

    @Schema(description = "온보딩 완료 여부", example = "true")
    private final boolean onboardingCompleted;

    @Schema(description = "해시태그 목록")
    private final List<HashtagSummary> hashtags;

    @Schema(description = "관심 아티스트 목록")
    private final List<ArtistSummary> favoriteArtists;

    @Schema(description = "공연 하루전 리마인더 이메일 수신 여부", example = "true")
    private final boolean concertReminderEmail;

    @Schema(description = "미확인 채팅 일일 다이제스트 이메일 수신 여부", example = "true")
    private final boolean chatNotificationEmail;

    @Schema(description = "생성일시")
    private final LocalDateTime createdAt;

    @Schema(description = "수정일시")
    private final LocalDateTime updatedAt;

    public UserResponseDto(User user, List<HashtagSummary> hashtags, List<ArtistSummary> favoriteArtists) {
        this.id = user.getId();
        this.authProvider = user.getAuthProvider();
        this.userId = user.getUserId();
        this.email = user.getEmail();
        this.nickname = user.getNickname();
        this.profileImageUrl = user.getProfileImageUrl();
        this.gender = user.getGender();
        this.birthDate = user.getBirthDate();
        this.onboardingCompleted = user.isOnboardingCompleted();
        this.hashtags = hashtags;
        this.favoriteArtists = favoriteArtists;
        this.concertReminderEmail = user.isConcertReminderEmail();
        this.chatNotificationEmail = user.isChatNotificationEmail();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }

    @Getter
    @Schema(description = "해시태그 요약 정보")
    public static class HashtagSummary {
        @Schema(description = "해시태그 ID", example = "1")
        private final Long id;

        @Schema(description = "해시태그", example = "굿즈")
        private final String tag;

        public HashtagSummary(Long id, String tag) {
            this.id = id;
            this.tag = tag;
        }
    }

    @Getter
    @Schema(description = "관심 아티스트 요약 정보")
    public static class ArtistSummary {
        @Schema(description = "관심 아티스트 등록 ID", example = "1")
        private final Long id;

        @Schema(description = "아티스트 PK", example = "10")
        private final Long artistId;

        @Schema(description = "아티스트 이름", example = "아이유")
        private final String artistName;

        @Schema(description = "아티스트 이미지 URL")
        private final String artistImageUrl;

        public ArtistSummary(Long id, Long artistId, String artistName, String artistImageUrl) {
            this.id = id;
            this.artistId = artistId;
            this.artistName = artistName;
            this.artistImageUrl = artistImageUrl;
        }
    }
}
