package com.loop.loop_backend.Block.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "차단 요청 DTO")
public class BlockRequestDto {

    @NotNull(message = "차단할 사용자 ID는 필수입니다")
    @Schema(description = "차단할 사용자 PK", example = "2")
    private Long targetUserId;
}