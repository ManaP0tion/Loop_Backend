package com.loop.loop_backend.Setlist.dto;

import com.loop.loop_backend.Song.domain.Song;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "예상 셋리스트 후보 곡. 로마자는 화면에 보이지 않고 검색에만 쓴다")
public record SongCandidateResponse(
        @Schema(description = "곡 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long songId,
        @Schema(description = "원제", requiredMode = Schema.RequiredMode.REQUIRED) String titleOriginal,
        @Schema(description = "로마자(검색용)", types = {"string", "null"}) String titleRomanized,
        @Schema(description = "한글 곡명", types = {"string", "null"}) String titleKo,
        @Schema(description = "앨범아트 URL(200x200, https)", types = {"string", "null"}) String albumArtUrl,
        @Schema(description = "정렬 순번(목록 순서)", requiredMode = Schema.RequiredMode.REQUIRED) int sortOrder
) {
    public static SongCandidateResponse from(Song song) {
        return new SongCandidateResponse(song.getId(), song.getTitleOriginal(), song.getTitleRomanized(),
                song.getTitleKo(), song.getAlbumArtUrl(), song.getSortOrder());
    }
}
