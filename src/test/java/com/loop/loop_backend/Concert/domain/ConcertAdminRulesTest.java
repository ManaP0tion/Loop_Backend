package com.loop.loop_backend.Concert.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Venue.domain.Venue;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 공연 관리자 수정 규칙(AD-01):
// - 새로 고를 수 있는 유형은 내한·페스티벌뿐. 페스티벌로 바꾸면 아티스트·예상 곡 수를 비운다. 페스티벌에는 둘 다 둘 수 없다.
// - 시작일은 종료일보다 늦을 수 없다. 기간을 바꾸면 새 기간 밖 날짜의 공연 시각은 지운다.
// - 공연 시각은 DAY 순서대로 받고 개수는 공연 일수와 같아야 한다. 기간이 없으면 받을 수 없다.
// - 예상 곡 수는 1 이상. 별칭·상품 코드는 공백·빈 값·중복을 정리한다. 숙소 노출을 켜려면 딥링크가 있어야 한다.
// - 비공개는 빈 칸이 있어도 저장된다. 공개 상태면 필수값(유형·공연명·포스터·기간·공연장, 내한이면 아티스트)이 있어야 한다.
// - 비공개 → 공개로 바뀌는 순간을 기록한다(이미 공개인 공연을 다시 공개로 저장해도 바뀌지 않는다).
class ConcertAdminRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 4, 12, 0);

    private static Concert jpopConcert() {
        return Concert.builder().title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST).build();
    }

    private static Concert publishableJpopConcert() {
        return Concert.builder()
                .title("YUURI LIVE")
                .category(ConcertCategory.J_POP_ARTIST)
                .posterUrl("https://poster")
                .startDate(LocalDate.of(2026, 12, 5))
                .endDate(LocalDate.of(2026, 12, 6))
                .linkedVenue(Venue.builder().name("인스파이어 아레나").address("인천광역시 중구").build())
                .artist(Artist.builder().name("Yuuri").category(ConcertCategory.J_POP_ARTIST).build())
                .build();
    }

    private static void assertRejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 유형 ----------

    @Test
    void 국내_유형으로는_바꿀_수_없다() {
        Concert concert = jpopConcert();

        assertRejected(() -> concert.changeCategory(ConcertCategory.DOMESTIC_ARTIST));
        assertRejected(() -> concert.changeCategory(ConcertCategory.DOMESTIC_FESTIVAL));
    }

    @Test
    void 페스티벌로_바꾸면_아티스트와_예상_곡_수가_비워진다() {
        Concert concert = publishableJpopConcert();
        concert.changeExpectedSongCount(20);

        concert.changeCategory(ConcertCategory.JAPAN_FESTIVAL);

        assertThat(concert.getArtist()).isNull();
        assertThat(concert.getExpectedSongCount()).isNull();
    }

    @Test
    void 페스티벌에는_아티스트도_예상_곡_수도_둘_수_없다() {
        Concert festival = Concert.builder().title("SUMMER SONIC").category(ConcertCategory.JAPAN_FESTIVAL).build();

        assertRejected(() -> festival.changeArtist(Artist.builder().name("Yuuri").build()));
        assertRejected(() -> festival.changeExpectedSongCount(20));
    }

    @Test
    void 예상_곡_수는_1_이상이다() {
        assertRejected(() -> jpopConcert().changeExpectedSongCount(0));
    }

    // ---------- 기간과 공연 시각 ----------

    @Test
    void 시작일이_종료일보다_늦을_수_없다() {
        assertRejected(() -> jpopConcert().changePeriod(LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 5)));
    }

    @Test
    void 공연_시각은_DAY_순서대로_시작일부터_하루씩_날짜가_붙는다() {
        Concert concert = jpopConcert();
        concert.changePeriod(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6));

        concert.replaceShowtimes(Arrays.asList(LocalTime.of(18, 0), null));

        assertThat(concert.getShowtimes()).extracting(ConcertShowtime::getDate, ConcertShowtime::getStartTime)
                .containsExactly(
                        tuple(LocalDate.of(2026, 12, 5), LocalTime.of(18, 0)),
                        tuple(LocalDate.of(2026, 12, 6), null));
    }

    @Test
    void 공연_시각_개수가_공연_일수와_다르면_거절한다() {
        Concert concert = jpopConcert();
        concert.changePeriod(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6));

        assertRejected(() -> concert.replaceShowtimes(List.of(LocalTime.of(18, 0))));
        assertRejected(() -> concert.replaceShowtimes(Arrays.asList(LocalTime.of(18, 0), null, null)));
    }

    @Test
    void 기간이_없으면_공연_시각을_받을_수_없다() {
        assertRejected(() -> jpopConcert().replaceShowtimes(List.of(LocalTime.of(18, 0))));
    }

    @Test
    void 기간을_바꾸면_새_기간_밖의_공연_시각만_지워지고_같은_날짜의_시각은_남는다() {
        Concert concert = jpopConcert();
        concert.changePeriod(LocalDate.of(2026, 12, 5), LocalDate.of(2026, 12, 6));
        concert.replaceShowtimes(List.of(LocalTime.of(18, 0), LocalTime.of(17, 0)));

        concert.changePeriod(LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 7));

        assertThat(concert.getShowtimes()).extracting(ConcertShowtime::getDate).containsExactly(LocalDate.of(2026, 12, 6));
        assertThat(concert.getShowtimes().get(0).getStartTime()).isEqualTo(LocalTime.of(17, 0));
    }

    // ---------- 목록 정리 ----------

    @Test
    void 별칭과_상품_코드는_공백_빈_값_중복을_정리하고_순서는_유지한다() {
        Concert concert = jpopConcert();

        concert.replaceTitleAliases(Arrays.asList(" 유우리 ", "", "유우리", null, "Yuuri"));
        concert.replaceProductCodes(List.of("PCXP-1", "PCXP-1 ", "PCXP-2"));

        assertThat(concert.getTitleAliases()).containsExactly("유우리", "Yuuri");
        assertThat(concert.getProductCodes()).containsExactly("PCXP-1", "PCXP-2");
    }

    // ---------- 숙소 ----------

    @Test
    void 숙소_노출을_켜려면_딥링크가_있어야_한다() {
        Concert concert = jpopConcert();
        concert.changeLodgingVisible(true);

        assertRejected(concert::validateState);

        concert.changeLodgingUrl("https://trip.example/deeplink");
        concert.validateState(); // 딥링크가 있으면 통과
    }

    // ---------- 공개 상태 필수값 ----------

    @Test
    void 비공개는_필수값이_비어_있어도_저장된다() {
        Concert concert = jpopConcert(); // 포스터·기간·공연장·아티스트 없음

        concert.validateState();
    }

    @Test
    void 공개하려면_필수값이_모두_있어야_한다() {
        Concert noPoster = publishableJpopConcert();
        noPoster.updatePosterUrl(null);
        noPoster.changePublished(true, NOW);
        assertRejected(noPoster::validateState);

        Concert noVenue = publishableJpopConcert();
        noVenue.changeVenue(null);
        noVenue.changePublished(true, NOW);
        assertRejected(noVenue::validateState);

        Concert noPeriod = publishableJpopConcert();
        noPeriod.changePeriod(null, null);
        noPeriod.changePublished(true, NOW);
        assertRejected(noPeriod::validateState);

        Concert ready = publishableJpopConcert();
        ready.changePublished(true, NOW);
        ready.validateState(); // 모두 있으면 통과
    }

    @Test
    void 내한_공연은_아티스트가_있어야_공개된다() {
        Concert concert = publishableJpopConcert();
        concert.changeArtist(null);
        concert.changePublished(true, NOW);

        assertRejected(concert::validateState);
    }

    @Test
    void 페스티벌은_아티스트_없이_공개된다() {
        Concert festival = publishableJpopConcert();
        festival.changeCategory(ConcertCategory.JAPAN_FESTIVAL);
        festival.changePublished(true, NOW);

        festival.validateState();
    }

    @Test
    void V2_이전_국내_페스티벌은_아티스트_없이도_공개_상태로_수정된다() {
        Concert domestic = Concert.builder()
                .title("어쩌다 페스티벌").category(ConcertCategory.DOMESTIC_FESTIVAL).posterUrl("https://poster")
                .startDate(LocalDate.of(2026, 11, 15)).endDate(LocalDate.of(2026, 11, 15))
                .linkedVenue(Venue.builder().name("연세대학교 대강당").address("서울특별시 서대문구").build())
                .build();
        domestic.changePublished(true, NOW);

        domestic.validateState();
    }

    // ---------- 공개 전환 시각 ----------

    @Test
    void 비공개에서_공개로_바뀌는_순간을_기록한다() {
        Concert concert = publishableJpopConcert();

        concert.changePublished(true, NOW);

        assertThat(concert.isPublished()).isTrue();
        assertThat(concert.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void 이미_공개인_공연을_다시_공개로_저장해도_공개_시각은_바뀌지_않는다() {
        Concert concert = publishableJpopConcert();
        concert.changePublished(true, NOW);

        concert.changePublished(true, NOW.plusDays(1));

        assertThat(concert.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void 비공개로_바꿨다가_다시_공개하면_새로_기록한다() {
        Concert concert = publishableJpopConcert();
        concert.changePublished(true, NOW);
        concert.changePublished(false, NOW.plusHours(1));

        concert.changePublished(true, NOW.plusDays(1));

        assertThat(concert.getPublishedAt()).isEqualTo(NOW.plusDays(1));
    }
}