package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ConcertResponseDto {

    private final Long id;
    private final Long artistId;
    private final String artistName;
    private final String title;
    private final String posterUrl;
    private final String venue;
    private final LocalDateTime performedAt;

    public static ConcertResponseDto from(Concert concert) {
        return ConcertResponseDto.builder()
                .id(concert.getId())
                .artistId(concert.getArtist() != null ? concert.getArtist().getId() : null)
                .artistName(concert.getArtist() != null ? concert.getArtist().getName() : null)
                .title(concert.getTitle())
                .posterUrl(concert.getPosterUrl())
                .venue(concert.getVenue())
                .performedAt(concert.getPerformedAt())
                .build();
    }
}
