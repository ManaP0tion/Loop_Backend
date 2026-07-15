package com.loop.loop_backend.Concert.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConcertCategory {
    J_POP_ARTIST("J-POP 아티스트"),
    DOMESTIC_ARTIST("국내 아티스트"),
    JAPAN_FESTIVAL("일본 페스티벌"),
    DOMESTIC_FESTIVAL("국내 페스티벌");

    private final String displayName;
}
