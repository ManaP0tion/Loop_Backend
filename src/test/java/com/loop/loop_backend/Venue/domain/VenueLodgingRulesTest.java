package com.loop.loop_backend.Venue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 숙소 규칙(공연장 단위):
// - 숙소는 노출 여부(On/Off)와 딥링크를 공연장마다 저장한다. 같은 공연장의 공연은 같은 숙소를 쓴다.
// - 노출을 켜려면 딥링크가 있어야 한다. 딥링크는 http/https 주소만, 1000자까지.
// - 사용자 화면에는 노출이 켜져 있을 때만 딥링크가 나간다.
// - 부분 수정은 null이면 변경 없음, 딥링크 ""는 비움.
class VenueLodgingRulesTest {

    private static final String DEEPLINK = "https://trip.example/deeplink?trip_sub=PF297519";

    private static Venue venue() {
        return Venue.builder().name("KSPO DOME").address("서울특별시 송파구").build();
    }

    @Test
    @DisplayName("새 공연장은 숙소 노출이 꺼져 있고 딥링크가 없다")
    void newVenue_HasLodgingOffWithoutUrl() {
        // given & when
        Venue venue = venue();

        // then
        assertThat(venue.isLodgingVisible()).isFalse();
        assertThat(venue.getLodgingUrl()).isNull();
        assertThat(venue.visibleLodgingUrl()).isNull();
    }

    @Test
    @DisplayName("딥링크와 함께 노출을 켜면 사용자 화면에 딥링크가 나간다")
    void changeLodging_WithUrlAndVisible_ExposesUrl() {
        // given
        Venue venue = venue();

        // when
        venue.changeLodging(true, "  " + DEEPLINK + "  ");

        // then
        assertThat(venue.isLodgingVisible()).isTrue();
        assertThat(venue.getLodgingUrl()).isEqualTo(DEEPLINK);
        assertThat(venue.visibleLodgingUrl()).isEqualTo(DEEPLINK);
    }

    @Test
    @DisplayName("노출이 꺼져 있으면 딥링크를 저장해 두어도 사용자 화면에는 나가지 않는다")
    void visibleLodgingUrl_WhenLodgingOff_ReturnsNull() {
        // given
        Venue venue = venue();

        // when
        venue.changeLodging(false, DEEPLINK);

        // then
        assertThat(venue.getLodgingUrl()).isEqualTo(DEEPLINK);
        assertThat(venue.visibleLodgingUrl()).isNull();
    }

    @Test
    @DisplayName("딥링크 없이 숙소 노출을 켜면 거절하고 값이 바뀌지 않는다")
    void changeLodging_VisibleWithoutUrl_ThrowsException() {
        // given
        Venue venue = venue();

        // when & then
        assertThatThrownBy(() -> venue.changeLodging(true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> venue.changeLodging(true, "  ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(venue.isLodgingVisible()).isFalse();
    }

    @Test
    @DisplayName("http/https 주소가 아닌 딥링크는 거절한다")
    void changeLodging_WithNonHttpUrl_ThrowsException() {
        // given
        Venue venue = venue();

        // when & then
        assertThatThrownBy(() -> venue.changeLodging(false, "javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> venue.changeLodging(false, "trip.com/hotels"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("제휴 딥링크는 1000자까지 저장되고 넘으면 거절한다")
    void changeLodging_UrlLength_AllowsUpTo1000() {
        // given
        Venue venue = venue();
        String prefix = "https://trip.example/?q=";
        String max = prefix + "a".repeat(1000 - prefix.length());

        // when
        venue.changeLodging(true, max);

        // then
        assertThat(venue.getLodgingUrl()).hasSize(1000);
        assertThatThrownBy(() -> venue.changeLodging(true, max + "a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("부분 수정에서 노출 여부만 보내면 저장된 딥링크는 유지된다")
    void patchLodging_WithVisibleOnly_KeepsUrl() {
        // given
        Venue venue = venue();
        venue.changeLodging(false, DEEPLINK);

        // when
        venue.patchLodging(true, null);

        // then
        assertThat(venue.visibleLodgingUrl()).isEqualTo(DEEPLINK);
    }

    @Test
    @DisplayName("부분 수정에서 둘 다 null이면 아무것도 바뀌지 않는다")
    void patchLodging_WithNothingSent_KeepsAll() {
        // given
        Venue venue = venue();
        venue.changeLodging(true, DEEPLINK);

        // when
        venue.patchLodging(null, null);

        // then
        assertThat(venue.isLodgingVisible()).isTrue();
        assertThat(venue.getLodgingUrl()).isEqualTo(DEEPLINK);
    }

    @Test
    @DisplayName("노출이 켜진 상태에서 딥링크만 비우면 거절한다")
    void patchLodging_ClearUrlWhileVisible_ThrowsException() {
        // given
        Venue venue = venue();
        venue.changeLodging(true, DEEPLINK);

        // when & then
        assertThatThrownBy(() -> venue.patchLodging(null, "")).isInstanceOf(IllegalArgumentException.class);
        assertThat(venue.getLodgingUrl()).isEqualTo(DEEPLINK);
    }

    @Test
    @DisplayName("노출을 끄면서 딥링크를 비우면 둘 다 반영된다")
    void patchLodging_TurnOffAndClearUrl_ClearsBoth() {
        // given
        Venue venue = venue();
        venue.changeLodging(true, DEEPLINK);

        // when
        venue.patchLodging(false, "");

        // then
        assertThat(venue.isLodgingVisible()).isFalse();
        assertThat(venue.getLodgingUrl()).isNull();
    }
}
