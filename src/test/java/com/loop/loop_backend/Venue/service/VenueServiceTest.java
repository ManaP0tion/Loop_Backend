package com.loop.loop_backend.Venue.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.ConcertImport.kopis.KopisClient.KopisFacility;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.dto.VenueRequestDto;
import com.loop.loop_backend.Venue.dto.VenueResponseDto;
import com.loop.loop_backend.Venue.dto.VenueUpdateRequestDto;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

// 공연장 관리(AD-02) 요구사항:
// - 직접 등록은 공연장명·주소가 필수, 나머지는 비워도 된다. 같은 이름의 공연장도 등록할 수 있다(주소로 구분).
// - 수정은 다른 PATCH API와 같이 null(또는 필드 없음)이면 변경 없음. 링크는 빈 문자열로 보내면 비우고, 수용 인원은 비울 수 없다.
//   좌표와 KOPIS 시설·홀 ID는 관리자 등록·수정으로 넣거나 바꿀 수 없다(KOPIS 자동 생성 때만 채워짐).
// - 연결된 공연이 있어도 삭제할 수 있고, 그 공연은 남되 공연장 연결만 비워진다.
// - 공연장마다 연결된 공연 수를 함께 보여준다. 목록은 공연장 수와 상관없이 같은 수의 쿼리로 센다(N+1 없음).
// - KOPIS 공연을 승인하면 같은 KOPIS 시설·홀의 공연장을 쓰고, 없으면 KOPIS 값(시설명 (홀명), 시·구 주소, 홀 좌석 수, 좌표)으로 만든다.
//   (시설 ID, 홀 ID)는 한 공연장에만 연결된다(홀 ID가 없어도 하나의 값으로 본다). KOPIS 조회에 실패해 이름·주소를 모르면 만들지 않는다.
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class VenueServiceTest {

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private EntityManager em;

    private VenueService venueService;

    @BeforeEach
    void setUp() {
        venueService = new VenueService(venueRepository);
    }

    private static VenueRequestDto request(String name, String address) {
        return new VenueRequestDto(name, address, null, null, null, null);
    }

    /** 부분 수정 요청: null 인자는 "변경 없음"이다. 링크를 비우려면 ""를 넘긴다. */
    private static VenueUpdateRequestDto patch(String name, String address, Integer capacity,
                                               String seatViewUrl, String kakaoMapUrl, String naverMapUrl) {
        return new VenueUpdateRequestDto(name, address, capacity, seatViewUrl, kakaoMapUrl, naverMapUrl);
    }

    private static final VenueUpdateRequestDto NOTHING_SENT = patch(null, null, null, null, null, null);

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private Venue kopisVenue() {
        return venueRepository.save(Venue.builder()
                .name("인스파이어 엔터테인먼트 리조트 (아레나)").address("인천광역시 중구").capacity(14483)
                .seatViewUrl("https://seat").kakaoMapUrl("https://kakao").naverMapUrl("https://naver")
                .latitude(37.4655301).longitude(126.3891177)
                .kopisFacilityId("FC003670").kopisHallId("FC003670-01")
                .build());
    }

    // ---------- 등록 ----------

    @Test
    void 공연장명과_주소만으로_등록되고_나머지는_비어_있다() {
        VenueResponseDto created = venueService.create(request("KSPO DOME", "서울특별시 송파구"));

        VenueResponseDto found = venueService.get(created.id());
        assertThat(found.name()).isEqualTo("KSPO DOME");
        assertThat(found.address()).isEqualTo("서울특별시 송파구");
        assertThat(found.capacity()).isNull();
        assertThat(found.seatViewUrl()).isNull();
        assertThat(found.kakaoMapUrl()).isNull();
        assertThat(found.naverMapUrl()).isNull();
    }

    @Test
    void 직접_등록한_공연장에는_좌표와_KOPIS_ID가_없다() {
        VenueResponseDto created = venueService.create(request("KSPO DOME", "서울특별시 송파구"));

        assertThat(created.latitude()).isNull();
        assertThat(created.longitude()).isNull();
        assertThat(created.kopisFacilityId()).isNull();
        assertThat(created.kopisHallId()).isNull();
    }

    @Test
    void 입력값의_앞뒤_공백은_지우고_빈_문자열인_선택_필드는_비운다() {
        VenueResponseDto created = venueService.create(new VenueRequestDto(
                "  KSPO DOME ", " 서울특별시 송파구 ", 15000, "  ", "", "https://naver.me/x "));

        assertThat(created.name()).isEqualTo("KSPO DOME");
        assertThat(created.address()).isEqualTo("서울특별시 송파구");
        assertThat(created.seatViewUrl()).isNull();
        assertThat(created.kakaoMapUrl()).isNull();
        assertThat(created.naverMapUrl()).isEqualTo("https://naver.me/x");
    }

    @Test
    void 같은_이름의_공연장도_여러_개_등록할_수_있다() {
        venueService.create(request("KBS홀", "부산광역시 수영구"));
        venueService.create(request("KBS홀", "경상남도 창원시 마산합포구"));

        assertThat(venueRepository.findAll()).hasSize(2);
    }

    // ---------- 조회 ----------

    @Test
    void 공연장명_일부로_대소문자_구분_없이_검색한다() {
        venueService.create(request("KSPO DOME", "서울특별시 송파구"));
        venueService.create(request("올림픽홀", "서울특별시 송파구"));

        assertThat(venueService.search("kspo", PageRequest.of(0, 20)))
                .extracting(VenueResponseDto::name).containsExactly("KSPO DOME");
    }

    @Test
    void 검색어가_없으면_전체를_돌려준다() {
        venueService.create(request("KSPO DOME", "서울특별시 송파구"));
        venueService.create(request("올림픽홀", "서울특별시 송파구"));

        assertThat(venueService.search("  ", PageRequest.of(0, 20))).hasSize(2);
    }

    @Test
    void 없는_공연장은_조회_수정_삭제할_수_없다() {
        assertErrorCode(() -> venueService.get(999L), ErrorCode.VENUE_NOT_FOUND);
        assertErrorCode(() -> venueService.update(999L, NOTHING_SENT), ErrorCode.VENUE_NOT_FOUND);
        assertErrorCode(() -> venueService.delete(999L), ErrorCode.VENUE_NOT_FOUND);
    }

    // ---------- 부분 수정 ----------

    @Test
    void 값을_보낸_필드만_바뀌고_null인_필드는_유지된다() {
        Venue venue = kopisVenue();

        venueService.update(venue.getId(), patch("인스파이어 아레나", null, null, "https://seat2", null, null));

        VenueResponseDto found = venueService.get(venue.getId());
        assertThat(found.name()).isEqualTo("인스파이어 아레나");
        assertThat(found.seatViewUrl()).isEqualTo("https://seat2");
        assertThat(found.address()).isEqualTo("인천광역시 중구");
        assertThat(found.capacity()).isEqualTo(14483);
        assertThat(found.kakaoMapUrl()).isEqualTo("https://kakao");
        assertThat(found.naverMapUrl()).isEqualTo("https://naver");
    }

    @Test
    void 링크는_빈_문자열로_보내면_비워진다() {
        Venue venue = kopisVenue();

        venueService.update(venue.getId(), patch(null, null, null, "", "  ", ""));

        VenueResponseDto found = venueService.get(venue.getId());
        assertThat(found.seatViewUrl()).isNull();
        assertThat(found.kakaoMapUrl()).isNull();
        assertThat(found.naverMapUrl()).isNull();
    }

    @Test
    void 수용_인원은_null로_보내도_비워지지_않는다() {
        Venue venue = kopisVenue();

        venueService.update(venue.getId(), patch(null, null, null, null, null, null));

        assertThat(venueService.get(venue.getId()).capacity()).isEqualTo(14483);
    }

    @Test
    void 수용_인원은_다른_값으로_바꿀_수_있다() {
        Venue venue = kopisVenue();

        venueService.update(venue.getId(), patch(null, null, 15000, null, null, null));

        assertThat(venueService.get(venue.getId()).capacity()).isEqualTo(15000);
    }

    @Test
    void 수정해도_KOPIS가_채운_좌표와_KOPIS_ID는_유지된다() {
        Venue venue = kopisVenue();

        // 관리자 화면이 보이는 필드를 모두 보내는 경우
        venueService.update(venue.getId(), patch("인스파이어 아레나", "인천광역시 중구", 15000, "", "", ""));

        VenueResponseDto found = venueService.get(venue.getId());
        assertThat(found.latitude()).isEqualTo(37.4655301);
        assertThat(found.longitude()).isEqualTo(126.3891177);
        assertThat(found.kopisFacilityId()).isEqualTo("FC003670");
        assertThat(found.kopisHallId()).isEqualTo("FC003670-01");
    }

    @Test
    void 아무_필드도_보내지_않으면_아무것도_바뀌지_않는다() {
        Venue venue = kopisVenue();

        VenueResponseDto updated = venueService.update(venue.getId(), NOTHING_SENT);

        assertThat(updated.name()).isEqualTo("인스파이어 엔터테인먼트 리조트 (아레나)");
        assertThat(updated.capacity()).isEqualTo(14483);
        assertThat(updated.seatViewUrl()).isEqualTo("https://seat");
    }

    @Test
    void 수정하는_값의_앞뒤_공백은_지운다() {
        Venue venue = kopisVenue();

        venueService.update(venue.getId(), patch("  인스파이어 아레나 ", null, null, null, null, null));

        assertThat(venueService.get(venue.getId()).name()).isEqualTo("인스파이어 아레나");
    }

    // ---------- 삭제 ----------

    @Test
    void 삭제하면_조회되지_않는다() {
        VenueResponseDto created = venueService.create(request("KSPO DOME", "서울특별시 송파구"));

        venueService.delete(created.id());

        assertErrorCode(() -> venueService.get(created.id()), ErrorCode.VENUE_NOT_FOUND);
    }

    @Test
    void 공연이_연결된_공연장도_삭제할_수_있고_공연은_남되_공연장_연결만_비워진다() {
        Venue venue = savedVenue("KSPO DOME");
        Concert concert = concertRepository.save(Concert.builder()
                .title("YOASOBI ASIA TOUR")
                .category(ConcertCategory.J_POP_ARTIST)
                .linkedVenue(venue)
                .build());
        em.flush();
        em.clear();

        venueService.delete(venue.getId());
        em.flush();
        em.clear();

        Concert reloaded = concertRepository.findById(concert.getId()).orElseThrow();
        assertThat(reloaded.getLinkedVenue()).isNull();
    }

    // ---------- 연결된 공연 수 ----------

    private Venue savedVenue(String name) {
        return venueRepository.save(Venue.builder().name(name).address("서울특별시 송파구").build());
    }

    private void linkConcerts(Venue venue, int count) {
        for (int i = 0; i < count; i++) {
            concertRepository.save(Concert.builder()
                    .title(venue.getName() + " 공연 " + i)
                    .category(ConcertCategory.J_POP_ARTIST)
                    .linkedVenue(venue)
                    .build());
        }
    }

    @Test
    void 상세에는_연결된_공연_수가_나온다() {
        Venue venue = savedVenue("KSPO DOME");
        linkConcerts(venue, 3);

        assertThat(venueService.get(venue.getId()).concertCount()).isEqualTo(3);
    }

    @Test
    void 연결된_공연이_없으면_공연_수는_0이다() {
        VenueResponseDto created = venueService.create(request("KSPO DOME", "서울특별시 송파구"));

        assertThat(created.concertCount()).isZero();
        assertThat(venueService.get(created.id()).concertCount()).isZero();
    }

    @Test
    void 수정_응답에도_연결된_공연_수가_나온다() {
        Venue venue = savedVenue("KSPO DOME");
        linkConcerts(venue, 2);

        assertThat(venueService.update(venue.getId(), patch("KSPO DOME(체조경기장)", null, null, null, null, null))
                .concertCount()).isEqualTo(2);
    }

    @Test
    void 목록에는_공연장마다_자기에게_연결된_공연_수가_나온다() {
        linkConcerts(savedVenue("KSPO DOME"), 2);
        savedVenue("올림픽홀");
        linkConcerts(savedVenue("핸드볼경기장"), 1);

        assertThat(venueService.search(null, PageRequest.of(0, 20)))
                .extracting(VenueResponseDto::name, VenueResponseDto::concertCount)
                .containsExactlyInAnyOrder(tuple("KSPO DOME", 2L), tuple("올림픽홀", 0L), tuple("핸드볼경기장", 1L));
    }

    /** action을 실행하는 동안 DB로 나간 SQL 수. 영속성 컨텍스트를 비워 캐시가 아닌 실제 조회를 센다. */
    private long queryCount(Runnable action) {
        em.flush();
        em.clear();
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        action.run();
        return stats.getPrepareStatementCount();
    }

    @Test
    void 목록의_공연_수는_공연장이_늘어도_쿼리_수가_늘지_않는다() {
        linkConcerts(savedVenue("공연장 1"), 1);
        long withOneVenue = queryCount(() -> venueService.search(null, PageRequest.of(0, 20)));

        for (int i = 2; i <= 5; i++) {
            linkConcerts(savedVenue("공연장 " + i), i);
        }
        long withFiveVenues = queryCount(() -> venueService.search(null, PageRequest.of(0, 20)));

        assertThat(withOneVenue).isPositive(); // 통계가 꺼져 있으면 둘 다 0이라 비교가 의미 없다
        assertThat(withFiveVenues).isEqualTo(withOneVenue);
    }

    // ---------- KOPIS 승인 시 공연장 찾기·만들기 ----------

    private static final KopisFacility INSPIRE = new KopisFacility(
            "인스파이어 엔터테인먼트 리조트", "아레나", "인천광역시 중구 공항문화로 127 (운서동)",
            37.4655301, 126.3891177, 14483);

    @Test
    void 같은_KOPIS_시설_홀의_공연장이_있으면_새로_만들지_않고_그_공연장을_쓴다() {
        Venue existing = venueRepository.save(Venue.builder().name("인스파이어 아레나").address("인천광역시 중구")
                .kopisFacilityId("FC003670").kopisHallId("FC003670-01").build());

        Venue venue = venueService.findOrCreateFromKopis("FC003670", "FC003670-01", INSPIRE);

        assertThat(venue.getId()).isEqualTo(existing.getId());
        assertThat(venue.getName()).isEqualTo("인스파이어 아레나"); // 관리자가 고친 이름이 유지된다
        assertThat(venueRepository.findAll()).hasSize(1);
    }

    @Test
    void 없으면_KOPIS_값으로_공연장을_만든다() {
        Venue venue = venueService.findOrCreateFromKopis("FC003670", "FC003670-01", INSPIRE);

        assertThat(venue.getId()).isNotNull();
        assertThat(venue.getName()).isEqualTo("인스파이어 엔터테인먼트 리조트 (아레나)");
        assertThat(venue.getAddress()).isEqualTo("인천광역시 중구");
        assertThat(venue.getCapacity()).isEqualTo(14483);
        assertThat(venue.getLatitude()).isEqualTo(37.4655301);   // 지도 링크를 넣기 전에도 좌표로 지도가 열린다
        assertThat(venue.getLongitude()).isEqualTo(126.3891177);
        assertThat(venue.getKopisFacilityId()).isEqualTo("FC003670");
        assertThat(venue.getKopisHallId()).isEqualTo("FC003670-01");
        assertThat(venue.getSeatViewUrl()).isNull();
        assertThat(venue.getKakaoMapUrl()).isNull();
        assertThat(venue.getNaverMapUrl()).isNull();
    }

    @Test
    void 같은_KOPIS_시설이라도_홀이_다르면_공연장을_따로_만든다() {
        KopisFacility ballroom = new KopisFacility("인스파이어 엔터테인먼트 리조트", "인스파이어 볼룸",
                "인천광역시 중구 공항문화로 127 (운서동)", 37.4655301, 126.3891177, 3000);

        Venue arena = venueService.findOrCreateFromKopis("FC003670", "FC003670-01", INSPIRE);
        Venue hall = venueService.findOrCreateFromKopis("FC003670", "FC003670-03", ballroom);

        assertThat(hall.getId()).isNotEqualTo(arena.getId());
        assertThat(hall.getName()).isEqualTo("인스파이어 엔터테인먼트 리조트 (인스파이어 볼룸)");
        assertThat(venueRepository.findAll()).hasSize(2);
    }

    @Test
    void 홀을_모르면_시설명만으로_공연장을_만든다() {
        KopisFacility noHall = new KopisFacility("고척스카이돔", null, "서울특별시 구로구 경인로 430", null, null, 16744);

        Venue venue = venueService.findOrCreateFromKopis("FC000001", null, noHall);

        assertThat(venue.getName()).isEqualTo("고척스카이돔");
        assertThat(venue.getAddress()).isEqualTo("서울특별시 구로구");
        assertThat(venue.getKopisHallId()).isNull();
    }

    @Test
    void 홀을_모르는_같은_시설은_다시_만들지_않고_기존_공연장을_쓴다() {
        KopisFacility noHall = new KopisFacility("고척스카이돔", null, "서울특별시 구로구 경인로 430", null, null, 16744);
        Venue first = venueService.findOrCreateFromKopis("FC000001", null, noHall);

        Venue second = venueService.findOrCreateFromKopis("FC000001", null, noHall);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(venueRepository.findAll()).hasSize(1);
    }

    @Test
    void KOPIS_조회에_실패해_이름이나_주소를_모르면_공연장을_만들지_않는다() {
        KopisFacility failed = new KopisFacility(null, null, null, null, null, null);

        assertThat(venueService.findOrCreateFromKopis("FC003670", "FC003670-01", failed)).isNull();
        assertThat(venueRepository.findAll()).isEmpty();
    }

    @Test
    void KOPIS_시설_ID가_없으면_공연장을_정하지_않는다() {
        assertThat(venueService.findOrCreateFromKopis(null, null, INSPIRE)).isNull();
        assertThat(venueRepository.findAll()).isEmpty();
    }
}