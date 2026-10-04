package com.loop.loop_backend.Venue.dto;

import com.loop.loop_backend.Venue.domain.Venue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공연장")
public record VenueResponseDto(

        @Schema(description = "공연장 PK", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "공연장명", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "주소(시·구 단위)", requiredMode = Schema.RequiredMode.REQUIRED)
        String address,

        @Schema(description = "수용 인원", types = {"integer", "null"})
        Integer capacity,

        @Schema(description = "좌석 시야(자리어때) 링크", types = {"string", "null"})
        String seatViewUrl,

        @Schema(description = "카카오맵 바로가기 링크", types = {"string", "null"})
        String kakaoMapUrl,

        @Schema(description = "네이버지도 바로가기 링크", types = {"string", "null"})
        String naverMapUrl,

        @Schema(description = "위도 (읽기 전용, KOPIS 자동 생성 시 채워짐). 지도 링크가 없을 때 프론트가 좌표로 링크를 만든다",
                types = {"number", "null"})
        Double latitude,

        @Schema(description = "경도 (읽기 전용, KOPIS 자동 생성 시 채워짐)", types = {"number", "null"})
        Double longitude,

        @Schema(description = "KOPIS 시설 ID (읽기 전용). 승인 시 자동 생성된 공연장이면 채워져 있다", types = {"string", "null"})
        String kopisFacilityId,

        @Schema(description = "KOPIS 홀 ID (읽기 전용)", types = {"string", "null"})
        String kopisHallId,

        @Schema(description = "이 공연장에 연결된 공연 수(비공개·지난 공연 포함). 삭제하면 이 공연들의 공연장이 비워진다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long concertCount
) {

    public static VenueResponseDto from(Venue venue, long concertCount) {
        return new VenueResponseDto(venue.getId(), venue.getName(), venue.getAddress(), venue.getCapacity(),
                venue.getSeatViewUrl(), venue.getKakaoMapUrl(), venue.getNaverMapUrl(),
                venue.getLatitude(), venue.getLongitude(),
                venue.getKopisFacilityId(), venue.getKopisHallId(), concertCount);
    }
}