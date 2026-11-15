package com.loop.loop_backend.Mail.dto;

import com.loop.loop_backend.Concert.domain.Concert;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 새 공연 알림 메일(NO.47)의 공연 한 줄. 대상 선정 트랜잭션이 끝난 뒤 메일을 만들기 때문에
 * 엔티티 대신 메일에 쓸 값만 담는다(트랜잭션 밖에서 지연 로딩이 일어나지 않게).
 */
public record NewConcertSummary(
        Long concertId,
        String title,
        String categoryName,
        String period,
        String venueName
) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public static NewConcertSummary of(Concert concert) {
        String venueName = concert.getLinkedVenue() != null ? concert.getLinkedVenue().getName() : concert.getVenue();
        return new NewConcertSummary(concert.getId(), concert.getTitle(), concert.getCategory().getDisplayName(),
                period(concert.getStartDate(), concert.getEndDate()), venueName);
    }

    private static String period(LocalDate start, LocalDate end) {
        if (end == null || end.equals(start)) return start.format(DATE);
        return start.format(DATE) + " ~ " + end.format(DATE);
    }
}
