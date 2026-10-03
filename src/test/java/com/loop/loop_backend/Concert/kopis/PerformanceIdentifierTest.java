package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 제목으로 공연을 식별하는 규칙을 검증한다. 제목은 가능한 한 실제 KOPIS 공연명(2026-10-03 목록)을 쓰고,
// 아티스트 이름·한글 표기·별칭은 실제 등록 데이터(ArtistDataInitializer)와 같은 값을 쓴다.
// - 등록 아티스트의 이름(name), 한글 표기(nameKo, " / "로 여러 개), 4자 이상 별칭이 제목에 단어 단위로 있으면 그 아티스트 공연이다.
//   대소문자는 구분하지 않는다.
// - 다른 단어의 일부로만 들어 있으면 매칭하지 않는다 ("라이브" 속 "이브" 같은 오탐 방지).
// - 일본 페스티벌(화이트리스트 이름, 또는 "[일본" + 페스티벌 표시어)은 아티스트와 상관없이 아티스트 없는 페스티벌이다.
// 페스티벌 표시어 + J-POP 아티스트 공연은 분류 요구사항이 바뀔 예정이라 여기서 다루지 않는다.
class PerformanceIdentifierTest {

    private final PerformanceIdentifier identifier = new PerformanceIdentifier();

    private static Artist jpop(String name, String nameKo, String nameAlias) {
        return Artist.builder()
                .name(name)
                .nameKo(nameKo)
                .nameAlias(nameAlias)
                .autoFetchConcerts(true)
                .category(ConcertCategory.J_POP_ARTIST)
                .build();
    }

    private static final Artist YUURI = jpop("Yuuri", "유우리", null);
    private static final Artist FUJII_KAZE = jpop("Fujii Kaze", "후지이 카제", null);
    private static final Artist KANA_BOON = jpop("KANA-BOON", "카나분", null);
    private static final Artist ELLEGARDEN = jpop("ELLEGARDEN", "엘르가든", null);
    private static final Artist OMOINOTAKE = jpop("Omoinotake", "오모이노타케", null);
    private static final Artist YOASOBI = jpop("YOASOBI", "요아소비", null);
    private static final Artist ADO = jpop("Ado", "아도", null);
    private static final Artist ZUTOMAYO = jpop("ZUTOMAYO", "즛토마요 / 계속 한밤중이면 좋을 텐데.", "즈토마요");
    private static final Artist HIGE_DANDISM = jpop("Official髭男dism", "오피셜히게단디즘", "히게단");
    private static final Artist EVE = jpop("Eve", "이브", null);
    private static final Artist LISA = jpop("LiSA", "리사", null);
    private static final Artist IRI = jpop("iri", "이리", null);
    private static final Artist FLOW = jpop("FLOW", "플로우", null);

    private static final List<Artist> ALL = List.of(YUURI, FUJII_KAZE, KANA_BOON, ELLEGARDEN, OMOINOTAKE,
            YOASOBI, ADO, ZUTOMAYO, HIGE_DANDISM, EVE, LISA, IRI, FLOW);

    private List<String> matchedNames(String title) {
        return identifier.identifyByTitle(title, ALL).artists().stream().map(Artist::getName).toList();
    }

    // ---------- 등록 아티스트 공연 ----------

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "YUURI LIVE [서울]                                       | Yuuri",       // 대소문자 무관
            "후지이 카제 Prema 월드 투어 [서울]                         | Fujii Kaze",  // 한글 표기
            "KANA-BOON, CRITICAL HIT PARADE: Asian Adventure         | KANA-BOON",   // 이름에 하이픈, 뒤에 쉼표
            "'ELLEGARDEN, Bad For Education Tour Ⅱ'                  | ELLEGARDEN",
            "Omoinotake One Man Tour in Seoul                        | Omoinotake",
    })
    void 제목에_등록_아티스트_이름이_있으면_그_아티스트의_J_POP_공연이다(String title, String artistName) {
        IdentificationResult result = identifier.identifyByTitle(title, ALL);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.category()).isEqualTo(ConcertCategory.J_POP_ARTIST);
        assertThat(result.matchReason()).isEqualTo(IdentificationResult.TITLE_MATCH);
        assertThat(result.artists()).extracting(Artist::getName).containsExactly(artistName);
    }

    @Test
    void 제목에_등록_아티스트가_여러_명이면_모두_매칭한다() {
        assertThat(matchedNames("YOASOBI × Ado SPECIAL LIVE")).containsExactlyInAnyOrder("YOASOBI", "Ado");
    }

    @Test
    void 한글_표기가_여러_개면_어느_표기로든_매칭한다() {
        assertThat(matchedNames("계속 한밤중이면 좋을 텐데. 내한공연")).containsExactly("ZUTOMAYO");
    }

    @Test
    void 네_글자_이상_별칭으로도_매칭한다() {
        assertThat(matchedNames("즈토마요 ASIA TOUR [서울]")).containsExactly("ZUTOMAYO");
    }

    // ---------- 매칭하지 않는 경우 ----------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "숲세권 라이브, 블루화 단독 공연: BLU Forest",                                     // "라이브" 속 "이브"
            "찰리 푸스 내한공연, Charlie Puth: Whatever's Clever! World Tour in Seoul",       // "Clever" 속 "eve"
            "수성르네상스 프로젝트, 젊은 예술가 리사이틀 Ⅷ. 베이시스트 이기욱 리사이틀 [대구]",   // "리사이틀" 속 "리사"
            "HARAKIRI FOR THE SKY LIVE IN SEOUL",                                           // "HARAKIRI" 속 "iri"
            "FLOWER CONCERT",                                                               // "FLOWER" 속 "FLOW"
    })
    void 아티스트_이름이_다른_단어의_일부로만_있으면_매칭하지_않는다(String title) {
        assertThat(identifier.identifyByTitle(title, ALL).isMatched()).isFalse();
    }

    @Test
    void 세_글자_이하_별칭만_제목에_있으면_매칭하지_않는다() {
        assertThat(identifier.identifyByTitle("히게단 내한공연", ALL).isMatched()).isFalse();
    }

    @Test
    void 제목에_등록_아티스트가_없으면_매칭하지_않는다() {
        IdentificationResult result = identifier.identifyByTitle("다이나믹 듀오 단독 콘서트: 가끔씩 오래 보자 [서울]", ALL);

        assertThat(result.isMatched()).isFalse();
        assertThat(result.artists()).isEmpty();
    }

    @Test
    void 등록_아티스트가_한_명도_없으면_매칭하지_않는다() {
        assertThat(identifier.identifyByTitle("YUURI LIVE [서울]", List.of()).isMatched()).isFalse();
    }

    @Test
    void 제목이_없으면_매칭하지_않는다() {
        assertThat(identifier.identifyByTitle(null, ALL).isMatched()).isFalse();
    }

    // ---------- 일본 페스티벌 ----------

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "SUMMER SONIC 2026",
            "summer sonic 2026",             // 대소문자 무관
            "FUJI ROCK FESTIVAL '26",
            "[일본] 록 페스티벌 2026",
            "[일본] OO FESTIVAL",
            "[일본] OO FEST 2026",
            "[일본] OO ROCK IN 2026",
            "[일본] OO JAM 2026",
    })
    void 일본_페스티벌이면_아티스트_없는_일본_페스티벌이다(String title) {
        IdentificationResult result = identifier.identifyByTitle(title, ALL);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.category()).isEqualTo(ConcertCategory.JAPAN_FESTIVAL);
        assertThat(result.matchReason()).isEqualTo(IdentificationResult.JAPAN_FESTIVAL);
        assertThat(result.artists()).isEmpty();
    }

    @Test
    void 일본_표시만_있고_페스티벌_표시가_없으면_페스티벌이_아니다() {
        IdentificationResult result = identifier.identifyByTitle("[일본] YUURI 단독 공연", ALL);

        assertThat(result.category()).isEqualTo(ConcertCategory.J_POP_ARTIST);
        assertThat(result.artists()).extracting(Artist::getName).containsExactly("Yuuri");
    }

    @Test
    void 일본_페스티벌_제목에_등록_아티스트가_있어도_아티스트_없는_페스티벌로_판정한다() {
        IdentificationResult result = identifier.identifyByTitle("SUMMER SONIC 2026 with YOASOBI", ALL);

        assertThat(result.category()).isEqualTo(ConcertCategory.JAPAN_FESTIVAL);
        assertThat(result.artists()).isEmpty();
    }
}