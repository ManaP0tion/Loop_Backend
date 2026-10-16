package com.loop.loop_backend.Venue.service;

import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.domain.VenueCapacity;
import com.loop.loop_backend.Venue.dto.VenueCsvImportResult;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 공연장 CSV 일괄 수정 요구사항:
// - 다운로드: 전체 공연장을 id 순으로, 업로드와 같은 헤더(+ 참고용 KOPIS ID). 엑셀 한글 깨짐 방지용 UTF-8 BOM.
// - 업로드: id로 공연장을 찾아 이름·주소·수용 인원·링크 3개를 바꾼다. 이름으로는 찾지 않는다(이름은 고칠 값).
//   빈 칸은 기존 값 유지, KOPIS ID 칸은 무시, 새 공연장은 만들지 않는다.
// - id가 비었거나 없는 공연장인 행, 잘못된 값(링크 형식·수용 인원·길이)이 있는 행은 통째로 건너뛰고 행 번호로 알려준다.
//   나머지 행은 반영된다.
// - 받은 파일을 고치지 않고 다시 올리면 아무것도 바뀌지 않는다(변경 없음으로 센다).
// - 깨진 CSV, UTF-8이 아닌 파일(한글이 깨져 읽힘)은 아무것도 반영하지 않고 400.
@DataJpaTest
class VenueCsvTest {

    private static final String HEADER =
            "id,name,address,capacity,seatViewUrl,kakaoMapUrl,naverMapUrl,kopisFacilityId,kopisHallId";

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private EntityManager em;

    private VenueService venueService;

    @BeforeEach
    void setUp() {
        venueService = new VenueService(venueRepository);
    }

    private Venue inspire() {
        return venueRepository.save(Venue.builder()
                .name("인스파이어 엔터테인먼트 리조트 (아레나)").address("인천광역시 중구").capacity(VenueCapacity.of(14483))
                .kakaoMapUrl("https://kko.to/old")
                .kopisFacilityId("FC003670").kopisHallId("FC003670-01")
                .build());
    }

    private Venue kspo() {
        return venueRepository.save(Venue.builder()
                .name("올림픽공원 (KSPO DOME)").address("서울특별시 송파구").capacity(VenueCapacity.of(15000))
                .kopisFacilityId("FC000017").kopisHallId("FC000017-02")
                .build());
    }

    private VenueCsvImportResult upload(String csv) {
        return upload(csv, StandardCharsets.UTF_8);
    }

    private VenueCsvImportResult upload(String csv, Charset charset) {
        VenueCsvImportResult result = venueService.importCsv(new ByteArrayInputStream(csv.getBytes(charset)));
        em.flush();
        em.clear();
        return result;
    }

    private Venue reload(Venue venue) {
        return venueRepository.findById(venue.getId()).orElseThrow();
    }

    private static void assertInvalidFormat(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST_FORMAT);
    }

    // ---------- 다운로드 ----------

    @Test
    void 다운로드는_BOM과_헤더_뒤에_전체_공연장을_id_순으로_내려준다() {
        Venue first = inspire();
        Venue second = kspo();

        String csv = new String(venueService.exportCsv(), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿" + HEADER);
        String[] lines = csv.substring(1).split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).isEqualTo(first.getId()
                + ",인스파이어 엔터테인먼트 리조트 (아레나),인천광역시 중구,\"14,483\",,https://kko.to/old,,FC003670,FC003670-01");
        assertThat(lines[2]).startsWith(second.getId() + ",올림픽공원 (KSPO DOME),");
    }

    @Test
    void 다운로드의_수용_인원은_화면과_같은_텍스트이고_그대로_다시_올려도_범위가_유지된다() {
        Venue venue = venueRepository.save(Venue.builder().name("YES24 라이브홀").address("서울특별시 광진구")
                .capacity(new VenueCapacity(1050, 2040)).build());

        String csv = new String(venueService.exportCsv(), StandardCharsets.UTF_8);
        VenueCsvImportResult result = upload(csv);

        assertThat(csv).contains(",YES24 라이브홀,서울특별시 광진구,\"1,050~2,040\",");
        assertThat(result.unchanged()).isEqualTo(1);
        assertThat(reload(venue).getCapacity()).isEqualTo(new VenueCapacity(1050, 2040));
    }

    @Test
    void 다운로드한_파일을_고치지_않고_다시_올리면_아무것도_바뀌지_않는다() {
        Venue venue = inspire();
        kspo();

        VenueCsvImportResult result = upload(new String(venueService.exportCsv(), StandardCharsets.UTF_8));

        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isEqualTo(2);
        assertThat(result.failedRows()).isEmpty();
        assertThat(reload(venue).getName()).isEqualTo("인스파이어 엔터테인먼트 리조트 (아레나)");
    }

    // ---------- 업로드: 반영 ----------

    @Test
    void id로_찾아_이름_주소_수용_인원_링크를_바꾼다() {
        Venue venue = inspire();

        VenueCsvImportResult result = upload(HEADER + "\n" + venue.getId()
                + ",인스파이어 아레나,인천 중구,15000,https://seat.example/inspire,https://kko.to/new,https://naver.me/new,,");

        assertThat(result.updated()).isEqualTo(1);
        Venue updated = reload(venue);
        assertThat(updated.getName()).isEqualTo("인스파이어 아레나");
        assertThat(updated.getAddress()).isEqualTo("인천 중구");
        assertThat(updated.getCapacity()).isEqualTo(VenueCapacity.of(15000));
        assertThat(updated.getSeatViewUrl()).isEqualTo("https://seat.example/inspire");
        assertThat(updated.getKakaoMapUrl()).isEqualTo("https://kko.to/new");
        assertThat(updated.getNaverMapUrl()).isEqualTo("https://naver.me/new");
    }

    @Test
    void 빈_칸은_기존_값을_유지한다() {
        Venue venue = inspire();

        upload(HEADER + "\n" + venue.getId() + ",,,,https://seat.example/inspire,,,,");

        Venue updated = reload(venue);
        assertThat(updated.getSeatViewUrl()).isEqualTo("https://seat.example/inspire");
        assertThat(updated.getName()).isEqualTo("인스파이어 엔터테인먼트 리조트 (아레나)");
        assertThat(updated.getAddress()).isEqualTo("인천광역시 중구");
        assertThat(updated.getCapacity()).isEqualTo(VenueCapacity.of(14483));
        assertThat(updated.getKakaoMapUrl()).isEqualTo("https://kko.to/old");
        assertThat(updated.getNaverMapUrl()).isNull();
    }

    @Test
    void 고칠_칸이_모두_비어_있으면_변경_없음으로_센다() {
        Venue venue = inspire();

        VenueCsvImportResult result = upload(HEADER + "\n" + venue.getId() + ",,,,,,,,");

        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isEqualTo(1);
    }

    @Test
    void KOPIS_ID_칸을_고쳐도_반영되지_않는다() {
        Venue venue = inspire();

        upload(HEADER + "\n" + venue.getId() + ",인스파이어 아레나,,,,,,FC999999,FC999999-09");

        Venue updated = reload(venue);
        assertThat(updated.getName()).isEqualTo("인스파이어 아레나");
        assertThat(updated.getKopisFacilityId()).isEqualTo("FC003670");
        assertThat(updated.getKopisHallId()).isEqualTo("FC003670-01");
    }

    @Test
    void 이름으로는_찾지_않는다_id가_빈_행은_실패로_알려준다() {
        Venue venue = inspire();

        VenueCsvImportResult result = upload(HEADER + "\n"
                + ",인스파이어 엔터테인먼트 리조트 (아레나),,,https://seat.example/inspire,,,,");

        assertThat(result.failedRows()).containsExactly(1L);
        assertThat(reload(venue).getSeatViewUrl()).isNull();
    }

    @Test
    void 새_공연장은_만들지_않는다_없는_id나_숫자가_아닌_id는_실패로_알려준다() {
        inspire();

        VenueCsvImportResult result = upload(HEADER + "\n"
                + "999999,새 공연장,서울특별시 중구,,,,,,\n"
                + "abc,새 공연장,서울특별시 중구,,,,,,");

        assertThat(result.failedRows()).containsExactly(1L, 2L);
        assertThat(venueRepository.findAll()).hasSize(1);
    }

    // ---------- 업로드: 잘못된 값 ----------

    @Test
    void 한_칸이라도_잘못되면_그_행은_통째로_건너뛰고_나머지_행은_반영된다() {
        Venue first = inspire();
        Venue second = kspo();

        VenueCsvImportResult result = upload(HEADER + "\n"
                + first.getId() + ",인스파이어 아레나,,,javascript:alert(1),,,,\n"
                + second.getId() + ",KSPO DOME,,,,,,,");

        assertThat(result.failedRows()).containsExactly(1L);
        assertThat(result.updated()).isEqualTo(1);
        // 이름 칸은 올바랐지만 같은 행의 링크가 잘못돼 이름도 바뀌지 않는다
        assertThat(reload(first).getName()).isEqualTo("인스파이어 엔터테인먼트 리조트 (아레나)");
        assertThat(reload(first).getSeatViewUrl()).isNull();
        assertThat(reload(second).getName()).isEqualTo("KSPO DOME");
    }

    @Test
    void 링크는_http_https_주소만_받는다() {
        Venue venue = inspire();

        VenueCsvImportResult result = upload(HEADER + "\n" + venue.getId() + ",,,,seat.example/inspire,,,,");

        assertThat(result.failedRows()).containsExactly(1L);
    }

    @Test
    void 수용_인원은_숫자나_최솟값_최댓값_범위만_받는다() {
        Venue venue = inspire();

        VenueCsvImportResult result = upload(HEADER + "\n"
                + venue.getId() + ",,,-1,,,,,\n"
                + venue.getId() + ",,,만오천,,,,,\n"
                + venue.getId() + ",,,400~200,,,,,\n"
                + venue.getId() + ",,,200~300~400,,,,,");

        assertThat(result.failedRows()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(reload(venue).getCapacity()).isEqualTo(VenueCapacity.of(14483));
    }

    @Test
    void 수용_인원은_PM_목록처럼_범위와_쉼표로_입력할_수_있다() {
        Venue venue = inspire();

        upload(HEADER + "\n" + venue.getId() + ",,,\"1,050~2,040\",,,,,");

        assertThat(reload(venue).getCapacity()).isEqualTo(new VenueCapacity(1050, 2040));
    }

    @Test
    void 이름과_주소는_255자까지_받는다() {
        Venue venue = inspire();

        VenueCsvImportResult result = upload(HEADER + "\n"
                + venue.getId() + "," + "가".repeat(256) + ",,,,,,,\n"
                + venue.getId() + ",," + "가".repeat(256) + ",,,,,,");

        assertThat(result.failedRows()).containsExactly(1L, 2L);
    }

    // ---------- 업로드: 파일 자체가 잘못됨 ----------

    @Test
    void BOM이_붙은_UTF_8_파일도_받는다() {
        Venue venue = inspire();

        upload("﻿" + HEADER + "\n" + venue.getId() + ",인스파이어 아레나,,,,,,,");

        assertThat(reload(venue).getName()).isEqualTo("인스파이어 아레나");
    }

    @Test
    void UTF_8이_아닌_파일은_한글이_깨지므로_아무것도_반영하지_않고_400() {
        Venue first = inspire();
        Venue second = kspo();
        // 엑셀의 'CSV (쉼표로 분리)' 저장 = CP949. 첫 행은 영문만이라 깨지지 않지만 둘째 행 한글이 깨진다.
        String csv = HEADER + "\n"
                + second.getId() + ",KSPO DOME,,,,,,,\n"
                + first.getId() + ",인스파이어 아레나,,,,,,,";

        assertInvalidFormat(() -> upload(csv, Charset.forName("MS949")));
        em.clear();
        assertThat(reload(second).getName()).isEqualTo("올림픽공원 (KSPO DOME)");
        assertThat(reload(first).getName()).isEqualTo("인스파이어 엔터테인먼트 리조트 (아레나)");
    }

    @Test
    void 깨진_CSV는_400() {
        Venue venue = inspire();

        assertInvalidFormat(() -> upload(HEADER + "\n" + venue.getId() + ",\"따옴표가 안 닫힘,,,,,,,"));
    }
}