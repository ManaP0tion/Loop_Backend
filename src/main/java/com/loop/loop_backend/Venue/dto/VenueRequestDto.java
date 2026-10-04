package com.loop.loop_backend.Venue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 공연장 직접 등록 요청 (KOPIS에 없는 공연장용). 관리자 화면의 필드만 받는다 -
 * 좌표와 KOPIS 시설·홀 ID는 KOPIS 공연 승인으로 자동 생성될 때만 채워진다.
 * 필수값 누락 등 검증 실패는 필드 구분 없이 400 하나로 응답한다.
 */
@Schema(description = "공연장 직접 등록 요청. 필수: name, address. 나머지는 생략하거나 null로 보내면 비어 있는 채로 등록된다")
public record VenueRequestDto(

        @NotBlank
        @Size(max = 255)
        @Schema(description = "공연장명 (필수). 같은 이름도 등록할 수 있다",
                example = "인스파이어 엔터테인먼트 리조트 (아레나)", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "주소 (필수). 시·구 단위까지만", example = "인천광역시 중구",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String address,

        @PositiveOrZero
        @Schema(description = "수용 인원(공연장 기준 평균치). 모르면 null", example = "14483", types = {"integer", "null"})
        Integer capacity,

        @Size(max = 500)
        @Schema(description = "좌석 시야(자리어때) 링크. 비우면 화면에서 해당 행 미노출", types = {"string", "null"})
        String seatViewUrl,

        @Size(max = 500)
        @Schema(description = "카카오맵 바로가기 링크", types = {"string", "null"})
        String kakaoMapUrl,

        @Size(max = 500)
        @Schema(description = "네이버지도 바로가기 링크", types = {"string", "null"})
        String naverMapUrl
) {
}