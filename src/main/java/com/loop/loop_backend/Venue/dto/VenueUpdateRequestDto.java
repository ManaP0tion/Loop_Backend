package com.loop.loop_backend.Venue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 공연장 부분 수정 요청 (PATCH). 다른 PATCH API와 같은 규칙으로, null(또는 필드 없음)은 변경 없음이다.
 * - 링크(좌석 시야·카카오맵·네이버지도)는 빈 문자열("")로 보내면 비운다.
 * - 필수값(name, address)은 비울 수 없어 빈 값·공백으로 보내면 400이다.
 * - 수용 인원은 비울 수 없다(null은 변경 없음) - 다른 값으로 바꾸는 것만 된다.
 * 좌표와 KOPIS 시설·홀 ID는 KOPIS 자동 생성 때만 채워지는 값이라 받지 않는다.
 */
@Schema(description = "공연장 부분 수정 요청. null이거나 보내지 않은 필드는 변경 없음(다른 PATCH API와 같은 규칙). " +
        "링크를 비우려면 빈 문자열(\"\")로 보낸다. 필수값(name, address)은 비울 수 없어 빈 값으로 보내면 400. " +
        "수용 인원은 비울 수 없다.")
public record VenueUpdateRequestDto(

        // null은 통과(변경 없음), 빈 값·공백만 있는 문자열은 거절
        @Pattern(regexp = ".*\\S.*")
        @Size(max = 255)
        @Schema(description = "공연장명 (필수값 - 비울 수 없음). null이면 변경 없음, 빈 값이면 400",
                example = "인스파이어 아레나", types = {"string", "null"})
        String name,

        @Pattern(regexp = ".*\\S.*")
        @Size(max = 255)
        @Schema(description = "주소 (필수값 - 비울 수 없음). 시·구 단위까지만. null이면 변경 없음, 빈 값이면 400",
                example = "인천광역시 중구", types = {"string", "null"})
        String address,

        @PositiveOrZero
        @Schema(description = "수용 인원. null이면 변경 없음 (비울 수 없다)", example = "14483", types = {"integer", "null"})
        Integer capacity,

        @Size(max = 500)
        @Schema(description = "좌석 시야(자리어때) 링크. null이면 변경 없음, 빈 문자열(\"\")이면 비움(화면에서 해당 행 미노출)",
                types = {"string", "null"})
        String seatViewUrl,

        @Size(max = 500)
        @Schema(description = "카카오맵 바로가기 링크. null이면 변경 없음, 빈 문자열(\"\")이면 비움(프론트는 좌표로 링크 생성)",
                types = {"string", "null"})
        String kakaoMapUrl,

        @Size(max = 500)
        @Schema(description = "네이버지도 바로가기 링크. null이면 변경 없음, 빈 문자열(\"\")이면 비움(프론트는 좌표로 링크 생성)",
                types = {"string", "null"})
        String naverMapUrl
) {
}