package com.loop.loop_backend.Concert.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30, nullable = false)
    private ConcertCategory category;

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
