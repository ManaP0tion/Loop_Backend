package com.loop.loop_backend.Block.dto;

import com.loop.loop_backend.Block.domain.Block;
// import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Schema(description = "차단한 사용자 응답 DTO")
public class BlockedUserResponseDto {

    @Schema(description = "차단 내역 ID", example = "1")
    private final Long blockId;

    @Schema(description = "차단된 사용자 PK", example = "2")
    private final Long userId;

    @Schema(description = "차단된 사용자 닉네임", example = "루퍼123")
    private final String nickname;

    @Schema(description = "차단된 사용자 프로필 이미지 URL")
    private final String profileImageUrl;

    @Schema(description = "차단된 사용자 나이 (생년월일 없으면 null)", example = "26")
    private final Integer age;

    // 나이대(그룹) 라벨로 보여주던 이전 방식 - 나이 숫자로 변경되면서 주석 처리, 필요해지면 복원
    // @Schema(description = "차단된 사용자 나이대 (생년월일 없으면 null)", example = "20대 초반")
    // private final String ageGroup;

    @Schema(description = "차단된 사용자 성별", example = "남")
    private final Gender gender;

    @Schema(description = "차단 일시")
    private final LocalDateTime blockedAt;

    public BlockedUserResponseDto(Block block) {
        User blocked = block.getBlocked();
        // AgeGroup group = blocked.getAgeGroup();

        this.blockId = block.getId();
        this.userId = blocked.getId();
        this.nickname = blocked.getNickname();
        this.profileImageUrl = blocked.getProfileImageUrl();
        this.age = blocked.getAge();
        // this.ageGroup = group != null ? group.getLabel() : null;
        this.gender = blocked.getGender();
        this.blockedAt = block.getCreatedAt();
    }
}