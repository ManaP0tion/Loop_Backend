package com.loop.loop_backend.Artist.dto;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ArtistResponseDto {

    private final Long id;
    private final String name;
    private final String baseName;
    private final String nameKo;
    private final String nameAlias;
    private final String imageUrl;
    private final ConcertCategory category;
    private final LocalDateTime createdAt;

    public static ArtistResponseDto from(Artist artist) {
        return ArtistResponseDto.builder()
                .id(artist.getId())
                .name(artist.getName())
                .baseName(artist.getBaseName())
                .nameKo(artist.getNameKo())
                .nameAlias(artist.getNameAlias())
                .imageUrl(artist.getImageUrl())
                .category(artist.getCategory())
                .createdAt(artist.getCreatedAt())
                .build();
    }
}
