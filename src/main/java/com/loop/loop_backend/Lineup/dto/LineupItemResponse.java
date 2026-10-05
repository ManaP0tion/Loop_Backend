package com.loop.loop_backend.Lineup.dto;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Lineup.domain.Lineup;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "라인업 항목(관리자·유저 공용). 목록은 노출 순서(displayOrder)대로 나온다")
public record LineupItemResponse(
        @Schema(description = "라인업 항목 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long lineupId,
        @Schema(description = "아티스트 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long artistId,
        @Schema(description = "아티스트 이름", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "아티스트 한글 이름", types = {"string", "null"}) String nameKo,
        @Schema(description = "아티스트 이미지 URL", types = {"string", "null"}) String imageUrl,
        @Schema(description = "DAY 번호(1부터)", requiredMode = Schema.RequiredMode.REQUIRED) int day,
        @Schema(description = "헤드라이너 여부", requiredMode = Schema.RequiredMode.REQUIRED) boolean headliner,
        @Schema(description = "전체 탭 노출 순서", requiredMode = Schema.RequiredMode.REQUIRED) int displayOrder
) {
    public static LineupItemResponse from(Lineup lineup) {
        Artist artist = lineup.getArtist();
        return new LineupItemResponse(lineup.getId(), artist.getId(), artist.getName(), artist.getNameKo(),
                artist.getImageUrl(), lineup.getDay(), lineup.isHeadliner(), lineup.getDisplayOrder());
    }
}
