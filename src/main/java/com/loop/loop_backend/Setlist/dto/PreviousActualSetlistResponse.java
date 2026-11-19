package com.loop.loop_backend.Setlist.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.repository.SetlistSong;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 이전 실제 셋리스트(F-1). 관리자가 지난 셋리스트 입력 폼을 채우는 데 쓴다 - tourName·performedOn·venueName·songs를 그대로 채우고
 * 확인·수정한 뒤 기존 저장 API로 저장한다. 삭제된 곡(deleted=true)은 저장할 수 없으니(400) 폼에서 빼야 한다.
 */
@Schema(description = "이전 실제 셋리스트(지난 셋리스트 불러오기용). tourName·performedOn·venueName은 입력 폼에 채울 제안값")
public record PreviousActualSetlistResponse(
        @Schema(description = "셋리스트 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long setlistId,
        @Schema(description = "그 셋리스트의 공연 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long concertId,
        @Schema(description = "투어명 제안값 = 그 공연의 공연명", requiredMode = Schema.RequiredMode.REQUIRED) String tourName,
        @Schema(description = "공연 날짜 제안값. 단독 = 공연 시작일, 페스티벌 = 시작일 + (첫 출연 DAY - 1). 공연 기간 미정이면 null",
                types = {"string", "null"}) LocalDate performedOn,
        @Schema(description = "장소 제안값 = 연결된 공연장 이름, 없으면 공연의 장소 문자열", types = {"string", "null"}) String venueName,
        @Schema(description = "곡 수", requiredMode = Schema.RequiredMode.REQUIRED) int songCount,
        @Schema(description = "곡 목록(공연 순서). 삭제된 곡은 deleted=true", requiredMode = Schema.RequiredMode.REQUIRED)
        List<SetlistSongResponse> songs
) {
    public static PreviousActualSetlistResponse of(Setlist setlist, List<SetlistSong> songList) {
        Performance performance = setlist.getPerformance();
        Concert concert = performance.getConcert();
        LocalDate performedOn = concert.getStartDate() == null ? null
                : concert.getStartDate().plusDays(performance.getDays().first() - 1L);
        String venueName = concert.getLinkedVenue() != null ? concert.getLinkedVenue().getName() : concert.getVenue();
        List<SetlistSongResponse> songs = new ArrayList<>();
        for (SetlistSong song : songList) {
            songs.add(SetlistSongResponse.of(songs.size() + 1, song));
        }
        return new PreviousActualSetlistResponse(setlist.getId(), concert.getId(), concert.getTitle(), performedOn,
                venueName, songs.size(), songs);
    }
}
