package com.loop.loop_backend.ConcertScrap.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 스크랩 목록 항목. 공연 요약(ConcertSummaryDto)의 필드를 그대로 펼치고 D-day·예매 알림만 덧붙인다 - 기존 키는 바뀌지 않는다.
 * 공연 요약은 공연 탭·검색 등이 함께 쓰는 사용자 무관 응답이라, 사용자별 값(알림)은 여기서만 붙인다.
 */
@Getter
@Schema(description = "스크랩한 공연 (공연 요약 + D-day·예매 알림)")
public class ScrapConcertDto {

    @JsonUnwrapped
    private final ConcertSummaryDto concert;

    // 필드명이 그대로 JSON 키(dday)가 되도록 dday로 둔다(공연 상세와 같은 키)
    @Schema(description = "오늘(KST) 기준 시작일까지 남은 일수 - 공연 상세와 같은 값. 당일 0, 이미 시작한 공연은 음수, " +
            "날짜 미정이면 null. 지난 공연(PAST)은 null", types = {"integer", "null"})
    private final Integer dday;

    @Schema(description = "예매 알림(선예매·일반예매) 중 하나라도 켜져 있으면 true. 공연 상세와 같은 규칙이라 켜 두었어도 " +
            "그 예매 일정이 없으면 꺼진 것으로 본다. 지난 공연(PAST)은 null", types = {"boolean", "null"})
    private final Boolean alarmOn;

    private ScrapConcertDto(ConcertSummaryDto concert, Integer dday, Boolean alarmOn) {
        this.concert = concert;
        this.dday = dday;
        this.alarmOn = alarmOn;
    }

    public static ScrapConcertDto upcoming(Concert concert, LocalDate today, boolean alarmOn) {
        return new ScrapConcertDto(ConcertSummaryDto.from(concert), concert.ddayFrom(today), alarmOn);
    }

    // 지난 공연에는 D-day·알림이 의미 없어 계산하지 않는다
    public static ScrapConcertDto past(Concert concert) {
        return new ScrapConcertDto(ConcertSummaryDto.from(concert), null, null);
    }
}
