package com.loop.loop_backend.Mail.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 예매 알림 메일 한 통(예매 일정 하나)의 내용. 발송은 대상 선정 트랜잭션 밖에서 일어나므로
 * 엔티티 대신 메일에 쓸 값만 담는다(지연 로딩이 일어나지 않게). 선예매·일반예매는 유형 이름만 다르다.
 */
public record TicketAlarmMail(
        Long concertId,
        String concertTitle,
        String saleTypeName,  // 선예매 / 일반예매
        String opensAt,       // 12월 2일 (화) 오후 8:00
        String opensTime,     // 오후 8:00 - 하루 전 메일 제목용
        String period,
        String venueName,
        List<TicketVendorInfo> vendors // 링크(url)가 없는 예매처는 이름만 보여준다
) {

    private static final DateTimeFormatter OPENS_AT = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN);
    private static final DateTimeFormatter OPENS_TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN);

    public static TicketAlarmMail of(Concert concert, TicketAlarmType type, LocalDateTime opensAt,
                                     List<TicketVendorInfo> vendors) {
        return new TicketAlarmMail(concert.getId(), concert.getTitle(), saleTypeName(type),
                opensAt.format(OPENS_AT), opensAt.format(OPENS_TIME),
                NewConcertSummary.period(concert.getStartDate(), concert.getEndDate()),
                NewConcertSummary.venueName(concert),
                vendors == null ? List.of() : List.copyOf(vendors));
    }

    private static String saleTypeName(TicketAlarmType type) {
        return switch (type) {
            case PRESALE -> "선예매";
            case GENERAL_SALE -> "일반예매";
        };
    }
}
