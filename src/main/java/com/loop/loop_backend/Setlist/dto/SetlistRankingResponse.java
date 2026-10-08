package com.loop.loop_backend.Setlist.dto;

import com.loop.loop_backend.Song.domain.Song;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 예상 셋리스트 순위(NO.51·52·54·66). 득표순, 동점이면 곡 정렬 순번 순 - 공동 순위 없음.
 * 전체를 한 번에 내려준다 - 미리보기(상위 5곡)·20곡 단위 더보기는 프론트가 이 목록으로 한다.
 */
@Schema(description = "예상 셋리스트 순위. 투표 0건이거나 셋리스트를 운영하지 않는 공연이면 songs가 빈 목록")
public record SetlistRankingResponse(
        @Schema(description = "하이라이트 개수 n(예상 곡 수). 셋리스트를 운영하지 않는 공연이면 null", example = "20",
                types = {"integer", "null"})
        Integer highlightCount,

        @Schema(description = "참여자 수", requiredMode = Schema.RequiredMode.REQUIRED)
        long participantCount,

        @Schema(description = "투표 마감 여부(공연 시작일 00:00 KST부터 true) - 투표·수정 버튼 비활성",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean votingClosed,

        @Schema(description = "득표한 곡 전체(순위대로)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<RankedSong> songs
) {

    @Schema(description = "순위 한 줄")
    public record RankedSong(
            @Schema(description = "순위(1부터, 공동 순위 없음)", requiredMode = Schema.RequiredMode.REQUIRED) int rank,
            @Schema(description = "곡 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long songId,
            @Schema(description = "원제", requiredMode = Schema.RequiredMode.REQUIRED) String titleOriginal,
            @Schema(description = "한글 곡명", types = {"string", "null"}) String titleKo,
            @Schema(description = "앨범아트 URL", types = {"string", "null"}) String albumArtUrl,
            @Schema(description = "득표 수", requiredMode = Schema.RequiredMode.REQUIRED) long votes,
            @Schema(description = "상위 n곡 하이라이트 여부", requiredMode = Schema.RequiredMode.REQUIRED) boolean highlighted,
            @Schema(description = "내가 고른 곡인지(비로그인·미투표면 false)", requiredMode = Schema.RequiredMode.REQUIRED) boolean mine
    ) {
        public static RankedSong of(int rank, Song song, long votes, boolean highlighted, boolean mine) {
            return new RankedSong(rank, song.getId(), song.getTitleOriginal(), song.getTitleKo(), song.getAlbumArtUrl(),
                    votes, highlighted, mine);
        }
    }
}
