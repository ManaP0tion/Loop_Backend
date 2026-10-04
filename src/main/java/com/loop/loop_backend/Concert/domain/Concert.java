package com.loop.loop_backend.Concert.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Venue.domain.Venue;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    /** 예상 곡 수(n). 단독 공연의 셋리스트 운영 기준(최대 선택 곡 수, 하이라이트 개수, 적중률 상위 n곡). */
    @Column(name = "expected_song_count")
    private Integer expectedSongCount;

    /** 숙소 섹션 노출 여부. Off면 섹션 자체를 그리지 않는다. 기존 공연은 Off. */
    @Column(name = "lodging_visible", nullable = false, columnDefinition = "boolean default false")
    private boolean lodgingVisible;

    /** 숙소 딥링크. 완성된 URL을 그대로 저장한다(자동 생성 없음). */
    @Column(name = "lodging_url", length = 1000)
    private String lodgingUrl;

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

    // 리포지토리 쿼리들의 COALESCE(endDate, startDate) 기준과 동일한 규칙.
    // 날짜 미정(둘 다 null)이면 "지난 공연"이 아니라고 본다 - 노출을 끊을 근거가 없어서 우선 계속 보여준다.
    public boolean isPast(LocalDate cutoff) {
        LocalDate reference = (endDate != null) ? endDate : startDate;
        return reference != null && reference.isBefore(cutoff);
    }
}
