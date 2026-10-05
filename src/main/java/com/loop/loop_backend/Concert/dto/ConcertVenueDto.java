package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Venue.domain.Venue;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공개 응답의 공연장(목록·예정 상세·지난 상세 공통).
 * 공연장 관리(AD-02)의 공연장이 연결돼 있으면 그 값을 쓰고, 연결 전 공연(공연장 관리가 생기기 전에 승인된 공연)은
 * 승인 때 저장한 KOPIS 장소 컬럼으로 채운다 - 이 경우 좌석 시야·지도 링크는 없다.
 * 어드민용 VenueResponseDto(KOPIS 코드, 연결 공연 수 등)는 공개 응답에 쓰지 않는다.
 */
@Schema(description = "공연장. 공연장 관리에 연결되지 않은 이전 공연은 KOPIS 장소 정보로 채워지고 링크는 null")
public record ConcertVenueDto(

        @Schema(description = "공연장 이름", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "주소. 공연장 관리 공연장은 시·구까지, 이전 공연은 KOPIS 전체 주소", types = {"string", "null"})
        String address,

        @Schema(description = "수용 인원", types = {"integer", "null"})
        Integer capacity,

        @Schema(description = "좌석 시야(자리어때) 링크", types = {"string", "null"})
        String seatViewUrl,

        @Schema(description = "카카오맵 링크", types = {"string", "null"})
        String kakaoMapUrl,

        @Schema(description = "네이버 지도 링크", types = {"string", "null"})
        String naverMapUrl,

        @Schema(description = "위도", types = {"number", "null"})
        Double latitude,

        @Schema(description = "경도", types = {"number", "null"})
        Double longitude
) {

    /** 공연장 정보가 전혀 없으면(직접 등록 후 공연장 미지정 등) null. */
    public static ConcertVenueDto of(Concert concert) {
        Venue venue = concert.getLinkedVenue();
        if (venue != null) {
            return new ConcertVenueDto(venue.getName(), venue.getAddress(), venue.getCapacity(),
                    venue.getSeatViewUrl(), venue.getKakaoMapUrl(), venue.getNaverMapUrl(),
                    venue.getLatitude(), venue.getLongitude());
        }
        if (concert.getVenue() == null) {
            return null;
        }
        return new ConcertVenueDto(concert.getVenue(), concert.getVenueAddress(), concert.getVenueCapacity(),
                null, null, null, concert.getVenueLatitude(), concert.getVenueLongitude());
    }
}