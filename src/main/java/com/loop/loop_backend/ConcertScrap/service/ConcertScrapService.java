package com.loop.loop_backend.ConcertScrap.service;

import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;

import java.util.List;

public interface ConcertScrapService {

    void scrap(Long userId, Long concertId);

    void unscrap(Long userId, Long concertId);

    List<ConcertSummaryDto> getMyScraps(Long userId, ConcertPeriod period);
}