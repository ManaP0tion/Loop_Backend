package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class ConcertRequestDto {

    private Long artistId;

    @NotBlank(message = "콘서트 제목은 필수입니다.")
    private String title;

    private String venue;
    private LocalDate startDate;
    private LocalDate endDate;
    private String price;
    private String ticketUrl;
    private String showtime;

    @NotNull(message = "카테고리는 필수입니다.")
    private ConcertCategory category;
}
