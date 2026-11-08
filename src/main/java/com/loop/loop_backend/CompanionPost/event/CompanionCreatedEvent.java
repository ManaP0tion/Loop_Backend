package com.loop.loop_backend.CompanionPost.event;

import com.loop.loop_backend.CompanionPost.domain.WatchDay;

import java.time.LocalDate;

/**
 * 동행 프로필 생성 알림용. 커밋 뒤 다른 스레드에서 읽으므로 엔티티 대신 값만 담는다(지연 로딩 불가).
 * watchDate는 공연 날짜가 미정이면 null.
 */
public record CompanionCreatedEvent(
        Long companionId,
        String concertTitle,
        WatchDay watchDay,
        LocalDate watchDate,
        String authorNickname,
        String messageToCompanion
) {
}