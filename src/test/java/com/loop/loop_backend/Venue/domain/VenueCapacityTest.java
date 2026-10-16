package com.loop.loop_backend.Venue.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 수용 인원 규칙: PM 목록처럼 "숫자" 또는 "최솟값~최댓값"으로 받아 숫자로 저장하고, 화면에는 천 단위 쉼표 텍스트로 보여준다.
class VenueCapacityTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "200~400       | 200~400",
            "1050~2040     | 1,050~2,040",
            "6830          | 6,830",
            "66700         | 66,700",
            "1,050 ~ 2,040 | 1,050~2,040",  // 화면에서 받은 텍스트를 그대로 다시 넣어도 된다
            "500~500       | 500",           // 최솟값과 최댓값이 같으면 범위가 아니다
            "0             | 0",
    })
    void 입력한_수용_인원은_천_단위_쉼표를_넣은_텍스트로_보인다(String input, String expected) {
        assertThat(VenueCapacity.parse(input).toText()).isEqualTo(expected);
    }

    @Test
    void 범위는_최솟값과_최댓값_숫자_두_개로_저장된다() {
        assertThat(VenueCapacity.parse("1,050~2,040")).isEqualTo(new VenueCapacity(1050, 2040));
    }

    @Test
    void 단일_값은_최댓값만_있다() {
        assertThat(VenueCapacity.parse("6830")).isEqualTo(new VenueCapacity(null, 6830));
        assertThat(VenueCapacity.parse("500~500")).isEqualTo(new VenueCapacity(null, 500));
    }

    @Test
    void 빈_값이면_수용_인원이_없고_텍스트도_null이다() {
        assertThat(VenueCapacity.parse(null).isEmpty()).isTrue();
        assertThat(VenueCapacity.parse("  ").isEmpty()).isTrue();
        assertThat(VenueCapacity.parse("").toText()).isNull();
        assertThat(VenueCapacity.of(null).toText()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"약 300", "300명", "-100", "400~200", "200~300~400", "~400", "200~", "2.5"})
    void 숫자나_최솟값_최댓값_범위가_아니면_거절한다(String input) {
        assertThatThrownBy(() -> VenueCapacity.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 최솟값만_있을_수는_없다() {
        assertThatThrownBy(() -> new VenueCapacity(200, null)).isInstanceOf(IllegalArgumentException.class);
    }
}