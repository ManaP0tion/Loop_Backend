package com.loop.loop_backend.CompanionPost.dto;

import com.loop.loop_backend.CompanionPost.domain.WatchDay;

import java.time.LocalDate;

public record ConcertReminderRow(
        Long recipientId,
        String recipientEmail,
        String recipientNickname,
        Long concertId,
        String concertTitle,
        String venue,
        LocalDate startDate,
        WatchDay watchDay
) {
}
