package com.loop.loop_backend.ConcertImport.kopis;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class KopisPerformance {
    private final String kopisId;
    private final String title;
    private final String posterUrl;
    private final String venue;
    private final LocalDate startDate;
    private final LocalDate endDate;
}
