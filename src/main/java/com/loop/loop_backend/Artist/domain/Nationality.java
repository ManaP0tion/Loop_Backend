package com.loop.loop_backend.Artist.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 아티스트 국적. 공연 유형(ConcertCategory)과는 별개다 - 공연은 형태(단독/페스티벌)만, 국적은 아티스트에만 둔다.
// 새 국적은 값만 추가하면 된다(DB에는 이름 문자열로 저장).
@Getter
@RequiredArgsConstructor
public enum Nationality {
    JP("일본"),
    KR("국내");

    private final String displayName;
}
