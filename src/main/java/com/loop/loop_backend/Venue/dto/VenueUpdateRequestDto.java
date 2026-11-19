package com.loop.loop_backend.Venue.dto;

import com.loop.loop_backend.Venue.domain.VenueCapacity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 공연장 부분 수정 요청 (PATCH). 다른 PATCH API와 같은 규칙으로, null(또는 필드 없음)은 변경 없음이다.
 * - 링크(좌석 시야·카카오맵·네이버지도)와 수용 인원은 빈 문자열("")로 보내면 비운다.
 * - 필수값(name, address)은 비울 수 없어 빈 값·공백으로 보내면 400이다.
 * - 수용 인원은 범위·단일 값을 문자열 하나로 받는다 - 숫자 두 칸으로 받으면 한쪽만 보냈을 때 다른 쪽이 남는지가 애매해진다.
 * 좌표와 KOPIS 시설·홀 ID는 KOPIS 자동 생성 때만 채워지는 값이라 받지 않는다.
 */
@Schema(description = "공연장 부분 수정 요청. null이거나 보내지 않은 필드는 변경 없음(다른 PATCH API와 같은 규칙). " +
        "링크·수용 인원을 비우려면 빈 문자열(\"\")로 보낸다. 필수값(name, address)은 비울 수 없어 빈 값으로 보내면 400.")
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

        // 형태(숫자 또는 숫자~숫자, 빈 문자열은 비움)는 여기서, 최솟값 > 최댓값은 VenueCapacity가 막는다 - 둘 다 400
        @Pattern(regexp = VenueCapacity.INPUT_PATTERN)
        @Size(max = 50)
        @Schema(description = "수용 인원. 숫자(\"6830\") 또는 범위(\"200~400\"), 쉼표·공백 허용. " +
                "null이면 변경 없음, 빈 문자열(\"\")이면 비움. 범위였던 공연장에 숫자 하나를 보내면 단일 값이 된다",
                example = "1050~2040", types = {"string", "null"})
        String capacity,

        @Size(max = 500)
        @Schema(description = "좌석 시야(오프메이트) 링크. null이면 변경 없음, 빈 문자열(\"\")이면 비움(화면에서 해당 행 미노출)",
                types = {"string", "null"})
        String seatViewUrl,

        @Size(max = 500)
        @Schema(description = "카카오맵 바로가기 링크. null이면 변경 없음, 빈 문자열(\"\")이면 비움(프론트는 좌표로 링크 생성)",
                types = {"string", "null"})
        String kakaoMapUrl,

        @Size(max = 500)
        @Schema(description = "네이버지도 바로가기 링크. null이면 변경 없음, 빈 문자열(\"\")이면 비움(프론트는 좌표로 링크 생성)",
                types = {"string", "null"})
        String naverMapUrl,

        @Schema(description = "숙소 섹션 노출 여부. null이면 변경 없음. 켜려면 딥링크가 있어야 한다(없으면 400)",
                example = "true", types = {"boolean", "null"})
        Boolean lodgingVisible,

        @Size(max = 1000)
        @Schema(description = "숙소 딥링크. null이면 변경 없음, 빈 문자열(\"\")이면 비움(노출이 켜져 있으면 400 - 함께 끄면 된다)",
                example = "https://trip.example/deeplink?trip_sub=PF297519", types = {"string", "null"})
        String lodgingUrl
) {
}