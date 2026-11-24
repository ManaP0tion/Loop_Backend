package com.loop.loop_backend.Mail.dto;

import com.loop.loop_backend.Concert.domain.Concert;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
        return new NewConcertSummary(concert.getId(), concert.getTitle(), concert.getCategory().getDisplayName(),
                period(concert.getStartDate(), concert.getEndDate()), venueName(concert));
    }

    /** 메일 제목. 여러 공연을 한 통에 묶으면 첫 공연명 뒤에 나머지 개수를 붙인다. */
    public static String mailSubject(List<NewConcertSummary> concerts) {
        String subject = "[Loop 신규 공연] " + concerts.get(0).title();
        return concerts.size() == 1 ? subject : subject + " 외 " + (concerts.size() - 1) + "건";
    }

    // 공연 메일(새 공연·예매 알림)이 같은 형식으로 쓴다
    static String venueName(Concert concert) {
        return concert.getLinkedVenue() != null ? concert.getLinkedVenue().getName() : concert.getVenue();
    }

    static String period(LocalDate start, LocalDate end) {
        if (start == null) return "날짜 미정";
        if (end == null || end.equals(start)) return start.format(DATE);
        return start.format(DATE) + " ~ " + end.format(DATE);
    }
}
