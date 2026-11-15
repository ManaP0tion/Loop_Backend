package com.loop.loop_backend.Mail.dto;

import java.time.LocalDate;

public record ConcertReminderSummary(
        Long concertId,
        String concertTitle,
        String venue,
        LocalDate watchDate
) {
}
