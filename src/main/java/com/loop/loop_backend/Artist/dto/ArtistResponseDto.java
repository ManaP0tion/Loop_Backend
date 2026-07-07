package com.loop.loop_backend.Artist.dto;

import com.loop.loop_backend.Artist.domain.Artist;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ArtistResponseDto {

    private final Long id;
    private final String name;
    private final String imageUrl;
    private final LocalDateTime createdAt;

    public static ArtistResponseDto from(Artist artist) {
        return ArtistResponseDto.builder()
                .id(artist.getId())
                .name(artist.getName())
                .imageUrl(artist.getImageUrl())
                .createdAt(artist.getCreatedAt())
                .build();
    }
}
