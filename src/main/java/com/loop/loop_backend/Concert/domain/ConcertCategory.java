package com.loop.loop_backend.Concert.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 공연 형태. 아티스트 국적은 Artist.nationality에 있다.
@Getter
@RequiredArgsConstructor
public enum ConcertCategory {
    STAGE("단독 공연"),
    FESTIVAL("페스티벌");

    private final String displayName;
}
