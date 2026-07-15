package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConcertCategoryDto {

    private final String code;
    private final String displayName;

    public static ConcertCategoryDto from(ConcertCategory category) {
        return ConcertCategoryDto.builder()
                .code(category.name())
                .displayName(category.getDisplayName())
                .build();
    }
}
