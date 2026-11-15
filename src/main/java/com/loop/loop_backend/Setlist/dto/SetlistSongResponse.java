package com.loop.loop_backend.Setlist.dto;

import com.loop.loop_backend.Setlist.repository.SetlistSong;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "셋리스트의 곡 한 줄. 표기는 원제 + 한글(입력된 곡만) + 앨범아트")
public record SetlistSongResponse(
        @Schema(description = "공연 순서(1부터)", requiredMode = Schema.RequiredMode.REQUIRED) int position,
        @Schema(description = "곡 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long songId,
        @Schema(description = "원제", requiredMode = Schema.RequiredMode.REQUIRED) String titleOriginal,
        @Schema(description = "한글 곡명", types = {"string", "null"}) String titleKo,
        @Schema(description = "앨범아트 URL(지난 셋리스트 화면에서는 쓰지 않음)", types = {"string", "null"}) String albumArtUrl,
        @Schema(description = "곡 데이터에서 삭제된 곡. 셋리스트에는 그대로 남고, 후보·투표에서는 고를 수 없다(셋리스트 저장 시 400)",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean deleted
) {
    public static SetlistSongResponse of(int position, SetlistSong song) {
        return new SetlistSongResponse(position, song.songId(), song.titleOriginal(), song.titleKo(),
                song.albumArtUrl(), song.deleted());
    }
}
