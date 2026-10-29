package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

// 공연 탭의 대분류(내한/페스티벌). ConcertCategory(공연 형태)를 화면 탐색 단위로 묶은 것.
// 지금은 일본 아티스트 한정 스코프라 단독 공연 = 내한이다. 국내외로 넓히면 내한은 '단독 공연 + 해외 국적 아티스트'로 바뀌어야 한다.
@Getter
@RequiredArgsConstructor
public enum ConcertSection {
    DOMESTIC_TOUR("내한", List.of(ConcertCategory.STAGE)),
    FESTIVAL("페스티벌", List.of(ConcertCategory.FESTIVAL));

    private final String label;
    private final List<ConcertCategory> categories;
}