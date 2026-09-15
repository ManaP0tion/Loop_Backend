package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

// 공연 탭의 대분류(내한/페스티벌). ConcertCategory(세부 장르)를 화면 탐색 단위로 묶은 것.
// 지금은 J-pop 한정 스코프라 카테고리가 하나씩만 매핑돼 있지만, 국내 카테고리를 다시 들여올 때
// 이 목록에 DOMESTIC_ARTIST/DOMESTIC_FESTIVAL을 추가하기만 하면 되도록 매핑을 여기 한곳에 모아둔다.
@Getter
@RequiredArgsConstructor
public enum ConcertSection {
    DOMESTIC_TOUR("내한", List.of(ConcertCategory.J_POP_ARTIST)),
    FESTIVAL("페스티벌", List.of(ConcertCategory.JAPAN_FESTIVAL));

    private final String label;
    private final List<ConcertCategory> categories;
}