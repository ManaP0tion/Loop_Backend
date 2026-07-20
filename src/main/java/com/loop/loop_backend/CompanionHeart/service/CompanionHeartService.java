package com.loop.loop_backend.CompanionHeart.service;

import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertDetailDto;
import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertSummaryDto;

import java.util.List;

public interface CompanionHeartService {

    void heartCompanion(Long userId, Long companionId);

    void unheartCompanion(Long userId, Long companionId);

    List<HeartedConcertSummaryDto> getMyHeartedConcerts(Long userId);

    HeartedConcertDetailDto getMyHeartedCompanionsByConcert(Long userId, Long concertId);
}