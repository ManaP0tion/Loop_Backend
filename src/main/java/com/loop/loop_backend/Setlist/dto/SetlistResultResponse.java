package com.loop.loop_backend.Setlist.dto;

import com.loop.loop_backend.Setlist.domain.HitGrade;
import com.loop.loop_backend.Song.domain.Song;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 공연 후 결과(NO.60·61·62·68). 적중률 = 맞힌 곡 수 ÷ 실제 셋리스트 곡 수(같은 곡 중복 연주는 1회, 순서·앵코르 무시).
 * 실제 셋리스트가 아직 없으면 ready=false - 적중률·셋리스트 두 섹션 모두 대기 문구.
 */
@Schema(description = "예상 셋리스트 결과. ready=false면 나머지는 null·빈 목록")
public record SetlistResultResponse(

        @Schema(description = "실제 셋리스트 저장 여부. false면 대기 문구", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean ready,

        @Schema(description = "참여자 수", requiredMode = Schema.RequiredMode.REQUIRED)
        long participantCount,

        @Schema(description = "팬 적중률: 득표 상위 n곡 중 실제로 나온 곡 기준", types = {"object", "null"})
        HitRate overall,

        @Schema(description = "전체 평균: 투표자 개인 적중률의 평균(%). 투표자가 없으면 null", example = "48",
                types = {"integer", "null"})
        Integer averagePercent,

        @Schema(description = "내 적중률. 비로그인·미투표면 null(팬 적중률로 대체해 보여준다)", types = {"object", "null"})
        HitRate mine,

        @Schema(description = "실제 셋리스트(공연 순서대로)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ResultSong> songs,

        @Schema(description = "예상했지만 나오지 않은 곡(득표순). 내 기준이면 mine=true, 팬 기준이면 fanPredicted=true만 골라 쓴다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<MissedSong> missedSongs
) {

    public static SetlistResultResponse waiting(long participantCount) {
        return new SetlistResultResponse(false, participantCount, null, null, null, List.of(), List.of());
    }

    @Schema(description = "적중률")
    public record HitRate(
            @Schema(description = "맞힌 곡 수 k", requiredMode = Schema.RequiredMode.REQUIRED) int hitCount,
            @Schema(description = "실제 셋리스트 곡 수 N(중복 연주는 1회)", requiredMode = Schema.RequiredMode.REQUIRED) int totalCount,
            @Schema(description = "적중률(%, 반올림)", example = "65", requiredMode = Schema.RequiredMode.REQUIRED) int percent,
            @Schema(description = "등급: LOW(0–49, 회색) / MID(50–69) / HIGH(70–84) / TOP(85–100)",
                    requiredMode = Schema.RequiredMode.REQUIRED) HitGrade grade
    ) {
        public static HitRate of(int hitCount, int totalCount) {
            int percent = (int) Math.round(hitCount * 100.0 / totalCount);
            return new HitRate(hitCount, totalCount, percent, HitGrade.of(percent));
        }
    }

    @Schema(description = "실제 셋리스트 한 줄")
    public record ResultSong(
            @Schema(description = "공연 순서(1부터)", requiredMode = Schema.RequiredMode.REQUIRED) int position,
            @Schema(description = "곡 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long songId,
            @Schema(description = "원제", requiredMode = Schema.RequiredMode.REQUIRED) String titleOriginal,
            @Schema(description = "한글 곡명", types = {"string", "null"}) String titleKo,
            @Schema(description = "앨범아트 URL", types = {"string", "null"}) String albumArtUrl,
            @Schema(description = "팬 예상 곡(득표 상위 n곡) - 배경 표시", requiredMode = Schema.RequiredMode.REQUIRED) boolean fanPredicted,
            @Schema(description = "내 예상 곡 - 체크 표시(비로그인·미투표면 false)", requiredMode = Schema.RequiredMode.REQUIRED) boolean mine,
            @Schema(description = "아무도 예상하지 못한 곡(득표 0)", requiredMode = Schema.RequiredMode.REQUIRED) boolean unexpected
    ) {
        public static ResultSong of(int position, Song song, boolean fanPredicted, boolean mine, boolean unexpected) {
            return new ResultSong(position, song.getId(), song.getTitleOriginal(), song.getTitleKo(), song.getAlbumArtUrl(),
                    fanPredicted, mine, unexpected);
        }
    }

    @Schema(description = "예상했지만 나오지 않은 곡")
    public record MissedSong(
            @Schema(description = "곡 PK", requiredMode = Schema.RequiredMode.REQUIRED) Long songId,
            @Schema(description = "원제", requiredMode = Schema.RequiredMode.REQUIRED) String titleOriginal,
            @Schema(description = "한글 곡명", types = {"string", "null"}) String titleKo,
            @Schema(description = "앨범아트 URL", types = {"string", "null"}) String albumArtUrl,
            @Schema(description = "득표 수", requiredMode = Schema.RequiredMode.REQUIRED) long votes,
            @Schema(description = "팬 예상 곡(득표 상위 n곡)", requiredMode = Schema.RequiredMode.REQUIRED) boolean fanPredicted,
            @Schema(description = "내가 고른 곡", requiredMode = Schema.RequiredMode.REQUIRED) boolean mine
    ) {
        public static MissedSong of(Song song, long votes, boolean fanPredicted, boolean mine) {
            return new MissedSong(song.getId(), song.getTitleOriginal(), song.getTitleKo(), song.getAlbumArtUrl(),
                    votes, fanPredicted, mine);
        }
    }
}
