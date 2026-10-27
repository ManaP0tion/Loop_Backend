package com.loop.loop_backend.Setlist.dto;

import com.loop.loop_backend.Setlist.dto.SetlistResultResponse.HitRate;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공연 단위 내 예상 셋리스트 적중률. 페스티벌은 헤드라이너 여러 팀을 합산한다(적중 곡 수 합 ÷ 실제 곡 수 합).
 * 내가 투표했고 실제 셋리스트가 저장된 출연 정보만 센다.
 */
@Schema(description = "공연 단위 내 적중률. ready=false면 나머지는 null")
public record ConcertSetlistResultResponse(

        @Schema(description = "실제 셋리스트가 저장된 출연 정보가 하나라도 있으면 true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean ready,

        @Schema(description = "합산 적중률. 비로그인·미투표(또는 투표한 아티스트의 실제 셋리스트 미저장)면 null", types = {"object", "null"})
        HitRate mine,

        @Schema(description = "합산 적중률 상위 n%(나보다 적중률이 높은 참여자 수 + 1 ÷ 참여자 수, 올림). mine이 null이면 null",
                example = "12", types = {"integer", "null"})
        Integer myTopPercent
) {

    public static ConcertSetlistResultResponse waiting() {
        return new ConcertSetlistResultResponse(false, null, null);
    }
}
