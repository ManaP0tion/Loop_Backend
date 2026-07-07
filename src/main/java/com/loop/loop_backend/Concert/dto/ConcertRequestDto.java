package com.loop.loop_backend.Concert.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ConcertRequestDto {

    private Long artistId;

    @NotBlank(message = "콘서트 제목은 필수입니다.")
    private String title;

    private String posterUrl;
    private String venue;
    private LocalDateTime performedAt;
}
