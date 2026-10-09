package com.loop.loop_backend.ConcertImport.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * KOPIS 공연이 수집 대상인지, 어떤 공연인지 판별한다. DB와 KOPIS를 건드리지 않는 순수 로직만 둔다 -
 * 출연진 조회(KOPIS 호출)와 저장은 KopisSyncService / ConcertImportWriter가 맡는다.
 */
@Component
public class PerformanceIdentifier {

    private static final int ALIAS_MIN_LENGTH = 4;

    // 표시어 없이도 이름만으로 일본 페스티벌로 보는 페스티벌
    private static final String[] KNOWN_JAPAN_FESTIVALS = {
            "FUJI ROCK",
            "SUMMER SONIC",
            "ROCK IN JAPAN",
            "JAPAN JAM",
            "COUNTDOWN JAPAN",
            "RISING SUN ROCK FESTIVAL",
            "VIVA LA ROCK",
            "MIYAKO ISLAND ROCK FESTIVAL",
            "ARABAKI",
            "METROCK",
            "JOIN ALIVE",
            "TREASURE ISLAND"
    };

    // 영문 표시어는 단어 단위로만 인정한다 ("FESTA" 같은 다른 단어 속 FES/FEST 오탐 방지)
    private static final String[] FESTIVAL_MARKERS_EN = {"FESTIVAL", "FEST", "FES"};
    // 한글은 "락페스티벌"처럼 붙여 쓰는 경우가 많아 포함 여부로 본다
    private static final String FESTIVAL_MARKER_KO = "페스티벌";

    /**
     * 제목만으로 식별한다 (API 호출 없음). 결과는 일본 페스티벌 또는 J-POP 아티스트 공연 두 가지다.
     * 페스티벌 판정에 J-POP 아티스트 출연 여부가 필요하므로 아티스트 매칭을 먼저 한다.
     * 페스티벌이면 출연 아티스트가 있어도 아티스트 없는 페스티벌 1건으로 본다.
     */
    public IdentificationResult identifyByTitle(String title, List<Artist> artists) {
        List<Artist> matched = artists.stream()
                .filter(a -> nameContainedInTitle(title, a))
                .toList();

        if (isJapanFestival(title, !matched.isEmpty())) {
            return IdentificationResult.japanFestival();
        }
        return IdentificationResult.jpopArtists(matched, IdentificationResult.TITLE_MATCH);
    }

    /**
     * 알려진 일본 페스티벌이거나, 페스티벌 표시어가 있으면서 일본 표시 또는 J-POP 아티스트가 있으면 일본 페스티벌.
     * 표시어만으로는 국내 페스티벌과 구분할 수 없어 일본 쪽 근거를 하나 더 요구한다.
     */
    private boolean isJapanFestival(String title, boolean hasJpopArtist) {
        if (title == null || title.isBlank()) return false;
        if (containsKnownJapanFestival(title)) return true;
        if (!hasFestivalMarker(title)) return false;
        return title.contains("[일본") || hasJpopArtist;
    }

    private boolean containsKnownJapanFestival(String title) {
        String upper = title.toUpperCase();
        return Arrays.stream(KNOWN_JAPAN_FESTIVALS).anyMatch(upper::contains);
    }

    private boolean hasFestivalMarker(String title) {
        if (title.contains(FESTIVAL_MARKER_KO)) return true;
        return Arrays.stream(FESTIVAL_MARKERS_EN).anyMatch(marker -> wordBoundaryMatch(title, marker));
    }

    /**
     * 타이틀에 일본/투어 관련 신호가 있을 때만 상세 API(prfcast)를 조회한다.
     * KOPIS 상세 응답의 cast 필드는 대부분 공백이라 무분별한 조회는 낭비이므로,
     * 국내 아티스트가 명확한 공연은 스킵한다.
     */
    public boolean shouldFetchCast(String title) {
        if (title == null) return false;
        String upper = title.toUpperCase();
        if (upper.contains("JAPAN") || upper.contains("ASIA TOUR")) return true;
        if (title.contains("일본") || title.contains("재팬")
                || title.contains("아시아 투어") || title.contains("내한")) return true;
        return containsJapaneseChar(title);
    }

    /** 상세 API에서 받은 출연진 문자열로 식별한다. 출연진이 없으면(null) 수집 대상이 아니다. */
    public IdentificationResult matchCast(String cast, List<Artist> artists) {
        if (cast == null) {
            return IdentificationResult.notMatched();
        }
        List<Artist> matched = artists.stream()
                .filter(a -> castMatches(cast, a))
                .toList();
        return IdentificationResult.jpopArtists(matched, IdentificationResult.CAST_MATCH);
    }

    /**
     * 타이틀에 아티스트 name / nameKo / 4자 이상의 nameAlias가 단어 단위로 포함되면 true.
     * 3자 이하의 짧은 alias(예: "미세스")는 일반 단어와 충돌해 오탐을 유발하므로 배제한다.
     */
    private boolean nameContainedInTitle(String title, Artist artist) {
        if (title == null) return false;

        if (wordBoundaryMatch(title, artist.getName())) return true;

        if (artist.getNameKo() != null) {
            boolean koHit = Arrays.stream(artist.getNameKo().split(" / "))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .anyMatch(ko -> wordBoundaryMatch(title, ko));
            if (koHit) return true;
        }

        String alias = artist.getNameAlias();
        if (alias != null && alias.length() >= ALIAS_MIN_LENGTH) {
            return wordBoundaryMatch(title, alias);
        }

        return false;
    }

    /**
     * keyword가 text 안에 단어 경계(앞뒤 문자가 문자/숫자가 아님)로 존재하면 true.
     * "이브"가 "라이브" 안에 묻히는 경우, "아도"가 "아도가" 조사에 붙는 경우를 차단한다.
     */
    private boolean wordBoundaryMatch(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) return false;
        String textLower = text.toLowerCase();
        String keyLower = keyword.toLowerCase();
        int keyLen = keyword.length();
        int idx = 0;
        while ((idx = textLower.indexOf(keyLower, idx)) >= 0) {
            boolean startOk = (idx == 0) || !Character.isLetterOrDigit(text.charAt(idx - 1));
            boolean endOk = (idx + keyLen >= text.length()) || !Character.isLetterOrDigit(text.charAt(idx + keyLen));
            if (startOk && endOk) return true;
            idx++;
        }
        return false;
    }

    /**
     * cast(콤마 구분)의 각 토큰이 아티스트 name / nameKo / nameAlias 중 하나와
     * 공백·특수문자를 무시한 lowercase 상태에서 정확 일치하면 true.
     */
    private boolean castMatches(String cast, Artist artist) {
        String[] tokens = cast.split(",");
        for (String rawToken : tokens) {
            String token = normalize(rawToken);
            if (token.isEmpty()) continue;
            if (token.equals(normalize(artist.getName()))) return true;
            if (artist.getNameKo() != null) {
                for (String ko : artist.getNameKo().split(" / ")) {
                    if (token.equals(normalize(ko))) return true;
                }
            }
            if (artist.getNameAlias() != null && token.equals(normalize(artist.getNameAlias()))) {
                return true;
            }
        }
        return false;
    }

    /** 문자·숫자만 남기고 lowercase 반환. null 안전. */
    private String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase();
    }

    /** 히라가나·카타카나·한자 유니코드 블록 중 하나라도 포함되면 true. */
    private boolean containsJapaneseChar(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                return true;
            }
        }
        return false;
    }
}