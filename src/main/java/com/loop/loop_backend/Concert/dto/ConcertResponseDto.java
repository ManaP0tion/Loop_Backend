package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ConcertResponseDto {

    private final Long id;
    private final Long artistId;
    private final String artistName;
    private final String kopisId;
    private final String title;
    private final String posterUrl;
    private final String venue;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final ConcertCategory category;
    private final String categoryDisplayName;
    private final long companionCount;

    public static ConcertResponseDto from(Concert concert, long companionCount) {
        ConcertCategory category = concert.getCategory();
        return ConcertResponseDto.builder()
                .id(concert.getId())
                .artistId(concert.getArtist() != null ? concert.getArtist().getId() : null)
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .kopisId(concert.getKopisId())
                .title(concert.getTitle())
                .posterUrl(concert.getPosterUrl())
                .venue(concert.getVenue())
                .startDate(concert.getStartDate())
                .endDate(concert.getEndDate())
                .category(category)
                .categoryDisplayName(category != null ? category.getDisplayName() : null)
                .companionCount(companionCount)
                .build();
    }
}
