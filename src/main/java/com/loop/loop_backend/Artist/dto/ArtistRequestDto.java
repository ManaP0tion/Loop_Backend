package com.loop.loop_backend.Artist.dto;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ArtistRequestDto {

    @NotBlank(message = "아티스트 이름은 필수입니다.")
    private String name;

    private String baseName;
    private String nameKo;
    private String nameAlias;
    private String imageUrl;
    private ConcertCategory category;
}
