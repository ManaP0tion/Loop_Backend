package com.loop.loop_backend.Venue.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// 공연장 주소는 시·구 단위까지만 둔다(AD-02). KOPIS 시설 주소(도로명·번지 포함)에서 앞쪽 행정구역만 남겨야 한다.
// 주소는 KOPIS 시설 API(adres)의 실제 형식을 쓴다.
class CityDistrictTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "인천광역시 중구 공항문화로 127 (운서동)       | 인천광역시 중구",        // 광역시 + 구
            "서울특별시 송파구 올림픽로 424             | 서울특별시 송파구",      // 특별시 + 구
            "경기도 성남시 분당구 성남대로 808          | 경기도 성남시 분당구",   // 도 + 시 + 일반구
            "경기도 가평군 가평읍 달전리 산20           | 경기도 가평군",         // 도 + 군
            "강원특별자치도 강릉시 경포로 365           | 강원특별자치도 강릉시",  // 특별자치도 + 시
            "세종특별자치시 한누리대로 2130            | 세종특별자치시",        // 구가 없는 특별자치시
    })
    void 주소에서_시_구_단위까지만_남긴다(String fullAddress, String expected) {
        assertThat(CityDistrict.from(fullAddress)).isEqualTo(expected);
    }

    @Test
    void 행정구역으로_시작하지_않는_주소는_그대로_둔다() {
        assertThat(CityDistrict.from("서울 송파구 올림픽로 424")).isEqualTo("서울 송파구 올림픽로 424");
    }

    @Test
    void 주소가_없으면_null이다() {
        assertThat(CityDistrict.from(null)).isNull();
        assertThat(CityDistrict.from("  ")).isNull();
    }
}