package com.loop.loop_backend.Setlist.dto;

import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.repository.SetlistSong;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Schema(description = "셋리스트(지난 셋리스트·실제 셋리스트 공용)")
public record SetlistResponse(
        @Schema(description = "셋리스트 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long setlistId,
        @Schema(description = "구분: RECENT(최근 공연) / PREVIOUS_VISIT(지난 내한) / ACTUAL(실제)",
                requiredMode = Schema.RequiredMode.REQUIRED) SetlistType type,
        @Schema(description = "투어명. 실제 셋리스트·미입력이면 null", types = {"string", "null"}) String tourName,
        @Schema(description = "공연 날짜. 실제 셋리스트면 null", types = {"string", "null"}) LocalDate performedOn,
        @Schema(description = "공연 장소. 실제 셋리스트면 null", types = {"string", "null"}) String venueName,
        @Schema(description = "곡 수(자동 계산)", requiredMode = Schema.RequiredMode.REQUIRED) int songCount,
        @Schema(description = "곡 목록(공연 순서대로)", requiredMode = Schema.RequiredMode.REQUIRED) List<SetlistSongResponse> songs
) {
    /** songs: 공연 순서대로, 삭제된 곡 포함(SetlistRepository.findSongsIncludingDeleted). */
    public static SetlistResponse from(Setlist setlist, List<SetlistSong> songList) {
        List<SetlistSongResponse> songs = new ArrayList<>();
        for (SetlistSong song : songList) {
            songs.add(SetlistSongResponse.of(songs.size() + 1, song));
        }
        return new SetlistResponse(setlist.getId(), setlist.getType(), setlist.getTourName(),
                setlist.getPerformedOn(), setlist.getVenueName(), songs.size(), songs);
    }
}
