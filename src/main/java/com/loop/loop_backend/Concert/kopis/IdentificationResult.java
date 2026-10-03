package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.ConcertCategory;

import java.util.List;

/**
 * KOPIS 공연 1건을 식별한 결과. 수집 대상이면 카테고리·매칭 근거·매칭된 아티스트를 담는다.
 *
 * 성공 여부는 아티스트 유무가 아니라 카테고리 유무로 판단한다 - 일본 페스티벌은 아티스트 없이도 수집 대상이다.
 * 아티스트는 목록이다 - 한 공연에 등록 아티스트가 여러 명 매칭될 수 있다 (페스티벌이면 빈 목록).
 */
public record IdentificationResult(ConcertCategory category, String matchReason, List<Artist> artists) {

    // ConcertImport.matchReason에 그대로 저장되는 값. 기존 행에는 CAST_MATCH/DOMESTIC_FESTIVAL도 남아 있어 문자열로 둔다.
    public static final String TITLE_MATCH = "TITLE_MATCH";
    public static final String CAST_MATCH = "CAST_MATCH";
    public static final String JAPAN_FESTIVAL = "JAPAN_FESTIVAL";

    private static final IdentificationResult NOT_MATCHED = new IdentificationResult(null, null, List.of());

    public static IdentificationResult notMatched() {
        return NOT_MATCHED;
    }

    public static IdentificationResult japanFestival() {
        return new IdentificationResult(ConcertCategory.JAPAN_FESTIVAL, JAPAN_FESTIVAL, List.of());
    }

    /** 매칭된 아티스트가 없으면 수집 대상이 아니므로 notMatched()를 돌려준다. */
    public static IdentificationResult jpopArtists(List<Artist> artists, String matchReason) {
        if (artists.isEmpty()) {
            return NOT_MATCHED;
        }
        return new IdentificationResult(ConcertCategory.J_POP_ARTIST, matchReason, List.copyOf(artists));
    }

    public boolean isMatched() {
        return category != null;
    }
}