package com.loop.loop_backend.Lineup.dto;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Performance.domain.Performance;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "라인업 항목(관리자·유저 공용). 목록은 노출 순서(displayOrder)대로 나온다. " +
        "헤드라이너는 lineupId를 셋리스트 API의 performanceId로 쓴다")
public record LineupItemResponse(
        @Schema(description = "라인업 항목 PK = 출연 정보 PK(performanceId)", requiredMode = Schema.RequiredMode.REQUIRED) Long lineupId,
        @Schema(description = "아티스트 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long artistId,
        @Schema(description = "아티스트 이름", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "아티스트 한글 이름", types = {"string", "null"}) String nameKo,
        @Schema(description = "아티스트 이미지 URL", types = {"string", "null"}) String imageUrl,
        @Schema(description = "일본/국내 구분(J_POP_ARTIST, DOMESTIC_ARTIST)", example = "J_POP_ARTIST",
                requiredMode = Schema.RequiredMode.REQUIRED) ConcertCategory category,
        @Schema(description = "출연 DAY 번호 목록(1부터, 오름차순). 같은 아티스트가 여러 DAY에 나와도 항목은 하나다",
                example = "[2, 3]", requiredMode = Schema.RequiredMode.REQUIRED) List<Integer> days,
        @Schema(description = "헤드라이너 여부. 출연한 모든 DAY에서 헤드라이너로 표시한다", requiredMode = Schema.RequiredMode.REQUIRED) boolean headliner,
        @Schema(description = "전체 탭 노출 순서", requiredMode = Schema.RequiredMode.REQUIRED) int displayOrder,
        @Schema(description = "예상 곡 수(헤드라이너 셋리스트 기준). 미입력이면 null - 셋리스트에서 20곡으로 처리",
                types = {"integer", "null"}) Integer expectedSongCount
) {
    public static LineupItemResponse from(Performance lineup) {
        Artist artist = lineup.getArtist();
        return new LineupItemResponse(lineup.getId(), artist.getId(), artist.getName(), artist.getNameKo(),
                artist.getImageUrl(), artist.getCategory(), List.copyOf(lineup.getDays()), lineup.isHeadliner(), lineup.getDisplayOrder(),
                lineup.getExpectedSongCount());
    }
}
