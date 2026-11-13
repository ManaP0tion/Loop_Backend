package com.loop.loop_backend.TicketAlarm.repository;

import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;

/** 내가 켠 예매 알림 한 건을 (공연 ID, 종류)로만 담는다 - 여러 공연의 알림 상태를 한 번에 확인할 때 쓴다. */
public record ConcertAlarmType(Long concertId, TicketAlarmType type) {
}