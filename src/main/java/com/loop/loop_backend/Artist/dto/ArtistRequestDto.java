package com.loop.loop_backend.Artist.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ArtistRequestDto {

    @NotBlank(message = "아티스트 이름은 필수입니다.")
    private String name;

    private String imageUrl;
}
