package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.Concert;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 공개 응답의 공연 아티스트. 지금은 내한 공연 1명, 페스티벌은 빈 목록 - 나중에 여러 명으로 늘릴 수 있게 목록으로 둔다. */
@Schema(description = "공연 아티스트")
public record ConcertArtistDto(

        @Schema(description = "아티스트 PK", requiredMode = Schema.RequiredMode.REQUIRED)
        Long artistId,

        @Schema(description = "아티스트명", requiredMode = Schema.RequiredMode.REQUIRED)
        String artistName,

        @Schema(description = "공식 인스타그램 URL. 없으면 null", types = {"string", "null"})
        String instagramUrl,

        @Schema(description = "공식 X URL. 없으면 null", types = {"string", "null"})
        String xUrl,

        @Schema(description = "공식 홈페이지 URL. 없으면 null", types = {"string", "null"})
        String homepageUrl
) {

    public static List<ConcertArtistDto> listOf(Concert concert) {
        Artist artist = concert.getArtist();
        return artist == null ? List.of() : List.of(new ConcertArtistDto(artist.getId(), artist.getName(),
                artist.getInstagramUrl(), artist.getXUrl(), artist.getHomepageUrl()));
    }
}