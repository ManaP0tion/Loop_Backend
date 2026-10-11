package com.loop.loop_backend.Concert.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.common.util.WebUrls;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(
    name = "concerts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"kopis_id", "artist_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artist artist;

    @Column(name = "kopis_id", length = 20)
    private String kopisId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "venue", length = 255)
    private String venue;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** KOPIS 상세: 티켓 가격 안내(pcseguidance). 예: "전석 99,000원" */
    @Column(name = "price", length = 500)
    private String price;

    /** KOPIS 상세: 예매처 URL(relate 첫 항목). */
    @Column(name = "ticket_url", length = 500)
    private String ticketUrl;

    /** KOPIS 상세: 공연 시간 안내(dtguidance). 예: "토요일(15:00,19:00)" */
    @Column(name = "showtime", length = 500)
    private String showtime;

    /**
     * KOPIS 상세: 예매처 목록(relates의 relatenm + relateurl), 여러 곳 가능. 승인 시에만 채워진다.
     * ticketUrl은 어드민 화면/DTO가 아직 쓰고 있어 유지하고, 승인할 때 이 목록의 첫 링크를 거기에도 채운다.
     * JSON 대신 TEXT로 둔 건 테스트(H2)에서도 문자열 그대로 저장되게 하려는 것.
     */
    @Convert(converter = TicketVendorListConverter.class)
    @Column(name = "ticket_vendors", columnDefinition = "TEXT")
    private List<TicketVendorInfo> ticketVendors;

    // 공연장 정보: KOPIS 시설 API(prfplc)에서 가져온다. 조회에 실패하면 전부 null.
    // update()는 이 필드들을 건드리지 않는다 - 어드민 수정 폼에 없어서 수정 요청 때 null로 덮어쓰이면 안 된다.

    /** 시설 주소(adres) */
    @Column(name = "venue_address", length = 500)
    private String venueAddress;

    /** 위도(la). 지도 링크는 프론트가 좌표로 만든다. */
    @Column(name = "venue_latitude")
    private Double venueLatitude;

    /** 경도(lo) */
    @Column(name = "venue_longitude")
    private Double venueLongitude;

    /** 수용 인원: 공연이 열리는 홀(mt13id 일치)의 seatscale, 홀을 못 찾으면 시설 전체 seatscale */
    @Column(name = "venue_capacity")
    private Integer venueCapacity;

    /**
     * 공연장 관리(AD-02)에 등록된 공연장. 승인 시 KOPIS 시설·홀 ID로 연결하고, 없으면 새로 만들어 연결한다.
     * 공연장을 삭제하면 DB가 연결만 끊고(NULL) 공연은 남긴다.
     * 위의 venue 문자열·주소·좌표 컬럼은 공개 API가 아직 쓰고 있어 함께 두고, 공연 모델 정리 때 이 연결로 옮긴다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Venue linkedVenue;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private ConcertCategory category;

    // ---------- 관리자 입력 항목 (AD-01) ----------

    /**
     * 공개 여부. 비공개는 사용자에게 '오픈 예정'(썸네일 회색, 상세 진입 불가)으로 보인다 - 숨김이 아니다.
     * 새 공연·선별 대기에서 등록한 공연은 비공개로 시작한다(builder 기본값 false).
     * 컬럼 기본값 true는 이 컬럼이 생길 때 기존 공연이 비공개로 바뀌지 않게 하려는 것(ddl-auto update가 기존 행을 기본값으로 채운다).
     */
    @Column(name = "published", nullable = false, columnDefinition = "boolean default true")
    private boolean published;

    /** 마지막으로 비공개 → 공개로 바뀐 시각. 공개 전환 1시간 후 새 공연 알림(NO.47)의 기준. 기존 공연은 비어 있다. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 예상 곡 수(n). 단독 공연의 셋리스트 운영 기준(최대 선택 곡 수, 하이라이트 개수, 적중률 상위 n곡). */
    @Column(name = "expected_song_count")
    private Integer expectedSongCount;

    /** 숙소 섹션 노출 여부. Off면 섹션 자체를 그리지 않는다. 기존 공연은 Off. */
    @Column(name = "lodging_visible", nullable = false, columnDefinition = "boolean default false")
    private boolean lodgingVisible;

    /** 숙소 딥링크. 완성된 URL을 그대로 저장한다(자동 생성 없음). */
    @Column(name = "lodging_url", length = 1000)
    private String lodgingUrl;

    /** 공연 특설 공식 사이트. 있는 공연만 상세에서 연결한다. http/https 주소만. */
    @Column(name = "official_site_url", length = 500)
    private String officialSiteUrl;

    // 여러 개 값은 공연에 딸린 목록으로 둔다. 공연이 DB 단에서 지워질 때(아티스트 삭제 연쇄)도 함께 지워지게 한다.

    /** 공연명 별칭(검색용). 예: 히게단, 오피셜히게단디즘 */
    @ElementCollection
    @CollectionTable(name = "concert_title_aliases", joinColumns = @JoinColumn(name = "concert_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Column(name = "alias", length = 255, nullable = false)
    @Builder.Default
    private List<String> titleAliases = new ArrayList<>();

    /** 관련 상품 코드(CD Japan 상품 코드 그대로). 0개면 상품 섹션을 그리지 않는다. */
    @ElementCollection
    @CollectionTable(name = "concert_product_codes", joinColumns = @JoinColumn(name = "concert_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Column(name = "product_code", length = 100, nullable = false)
    @Builder.Default
    private List<String> productCodes = new ArrayList<>();

    /** DAY별 공연 시각. 날짜 순. 하루 공연은 DAY 구분 없이 한 건. */
    @ElementCollection
    @CollectionTable(name = "concert_showtimes", joinColumns = @JoinColumn(name = "concert_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderBy("date ASC")
    @Builder.Default
    private List<ConcertShowtime> showtimes = new ArrayList<>();

    public void update(Artist artist, String title, String posterUrl, String venue,
                       LocalDate startDate, LocalDate endDate, ConcertCategory category,
                       String price, String ticketUrl, String showtime) {
        this.artist = artist;
        this.title = title;
        this.posterUrl = posterUrl;
        this.venue = venue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.category = category;
        this.price = price;
        this.ticketUrl = ticketUrl;
        this.showtime = showtime;
    }

    public void updateFromKopis(String title, String posterUrl, String venue,
                                LocalDate startDate, LocalDate endDate, ConcertCategory category) {
        this.title = title;
        this.posterUrl = posterUrl;
        this.venue = venue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.category = category;
    }

    public void updatePosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    // ---------- 관리자 수정 (AD-01) ----------
    // 규칙 위반은 IllegalArgumentException → GlobalExceptionHandler가 400(INVALID_INPUT) 하나로 응답한다.

    /** 관리자가 새로 고를 수 있는 공연 유형. 국내 유형은 V2 이전 데이터에만 남아 있다. */
    private static final Set<ConcertCategory> SELECTABLE_CATEGORIES =
            Set.of(ConcertCategory.J_POP_ARTIST, ConcertCategory.JAPAN_FESTIVAL);

    /** 유형 변경. 페스티벌로 바꾸면 페스티벌 화면에 없는 아티스트·예상 곡 수를 비운다(PATCH의 null은 '변경 없음'이라 프론트가 비울 수 없다). */
    public void changeCategory(ConcertCategory category) {
        if (!SELECTABLE_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("선택할 수 없는 공연 유형: " + category);
        }
        this.category = category;
        if (isFestival()) {
            this.artist = null;
            this.expectedSongCount = null;
        }
    }

    public void rename(String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("공연명은 비울 수 없다");
        this.title = title.trim();
    }

    /** 기간 변경. 새 기간 밖으로 밀려난 날짜의 공연 시각은 지운다(남겨 두면 보이지 않는 데이터가 된다). */
    public void changePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("시작일이 종료일보다 늦다");
        }
        this.startDate = startDate;
        this.endDate = endDate;
        this.showtimes.removeIf(s -> !isWithinPeriod(s.getDate()));
    }

    /**
     * DAY 순서대로 받은 시각으로 공연 시각을 바꾼다. i번째 시각 = 시작일 + i일. 미정인 DAY는 null.
     * 개수는 공연 일수와 같아야 한다(화면은 기간에 맞춰 DAY 입력칸을 만든다).
     */
    public void replaceShowtimes(List<LocalTime> startTimesByDay) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("공연 기간 없이 공연 시각을 정할 수 없다");
        }
        int days = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (startTimesByDay.size() != days) {
            throw new IllegalArgumentException("공연 시각 개수(" + startTimesByDay.size() + ")가 공연 일수(" + days + ")와 다르다");
        }
        this.showtimes.clear();
        for (int i = 0; i < days; i++) {
            this.showtimes.add(new ConcertShowtime(startDate.plusDays(i), startTimesByDay.get(i)));
        }
    }

    public void changeVenue(Venue venue) {
        this.linkedVenue = venue;
    }

    /** 아티스트는 내한 공연에만 둔다(페스티벌은 라인업으로 따로 관리). 지금은 1명, null이면 비움. */
    public void changeArtist(Artist artist) {
        if (artist != null && isFestival()) {
            throw new IllegalArgumentException("페스티벌에는 아티스트를 지정하지 않는다");
        }
        this.artist = artist;
    }

    /** 예상 곡 수는 단독(내한) 공연만, 1 이상. */
    public void changeExpectedSongCount(Integer expectedSongCount) {
        if (expectedSongCount != null && isFestival()) {
            throw new IllegalArgumentException("페스티벌에는 예상 곡 수를 두지 않는다");
        }
        if (expectedSongCount != null && expectedSongCount < 1) {
            throw new IllegalArgumentException("예상 곡 수는 1 이상");
        }
        this.expectedSongCount = expectedSongCount;
    }

    public void changeLodgingVisible(boolean lodgingVisible) {
        this.lodgingVisible = lodgingVisible;
    }

    /** 빈 문자열이면 비운다. */
    public void changeLodgingUrl(String lodgingUrl) {
        this.lodgingUrl = (lodgingUrl == null || lodgingUrl.isBlank()) ? null : lodgingUrl.trim();
    }

    /** 빈 문자열이면 비운다. http/https 주소가 아니면 400. */
    public void changeOfficialSiteUrl(String officialSiteUrl) {
        this.officialSiteUrl = WebUrls.normalize(officialSiteUrl);
    }

    public void replaceTitleAliases(List<String> aliases) {
        this.titleAliases.clear();
        this.titleAliases.addAll(cleanDistinct(aliases));
    }

    public void replaceProductCodes(List<String> productCodes) {
        this.productCodes.clear();
        this.productCodes.addAll(cleanDistinct(productCodes));
    }

    /** 공개 여부 변경. 비공개 → 공개로 바뀌는 순간을 기록한다(새 공연 알림 기준). */
    public void changePublished(boolean published, LocalDateTime now) {
        if (published && !this.published) {
            this.publishedAt = now;
        }
        this.published = published;
    }

    /**
     * 수정을 모두 반영한 뒤 상태를 검사한다.
     * - 항상: 숙소 노출 On이면 딥링크가 있어야 한다.
     * - 공개 상태일 때만: 필수값(유형·공연명·포스터·기간·공연장, 내한이면 아티스트 1명). 비공개는 빈 칸이 있어도 저장된다.
     * 이미 공개된 공연을 고칠 때도 검사하므로, 공연장이 아직 연결되지 않은 기존 공연은 수정하면서 공연장을 함께 골라야 한다.
     */
    public void validateState() {
        if (lodgingVisible && lodgingUrl == null) {
            throw new IllegalArgumentException("숙소 노출을 켜려면 딥링크가 있어야 한다");
        }
        if (!published) return;
        if (category == null || title == null || posterUrl == null || posterUrl.isBlank()
                || startDate == null || endDate == null || linkedVenue == null) {
            throw new IllegalArgumentException("공개하려면 필수값(유형·공연명·포스터·기간·공연장)을 모두 채워야 한다");
        }
        // 아티스트 필수는 내한 공연만. 국내 유형(V2 이전 데이터)은 그대로 수정할 수 있게 검사하지 않는다.
        if (category == ConcertCategory.J_POP_ARTIST && artist == null) {
            throw new IllegalArgumentException("내한 공연을 공개하려면 아티스트가 있어야 한다");
        }
    }

    public boolean isFestival() {
        return category == ConcertCategory.JAPAN_FESTIVAL;
    }

    private boolean isWithinPeriod(LocalDate date) {
        return startDate != null && endDate != null && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /** 앞뒤 공백 제거, 빈 값 제외, 중복 제거(처음 나온 순서 유지). */
    private static List<String> cleanDistinct(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();
    }

    // 리포지토리 쿼리들의 COALESCE(endDate, startDate) 기준과 동일한 규칙.
    // 날짜 미정(둘 다 null)이면 "지난 공연"이 아니라고 본다 - 노출을 끊을 근거가 없어서 우선 계속 보여준다.
    public boolean isPast(LocalDate cutoff) {
        LocalDate reference = (endDate != null) ? endDate : startDate;
        return reference != null && reference.isBefore(cutoff);
    }
}
