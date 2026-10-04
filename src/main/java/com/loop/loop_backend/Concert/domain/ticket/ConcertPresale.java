package com.loop.loop_backend.Concert.domain.ticket;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.TicketVendorListConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 선예매 1건(AD-01 예매 유형). 한 건 = 예매 일시 1개 + 예매처(이름 + URL) 여러 개. 공연마다 여러 건 등록할 수 있다.
 * 일반예매(ConcertGeneralSale)와 화면의 추가 버튼·API가 따로라 엔티티도 나눴다.
 * 선예매는 KOPIS 자동 입력이 없어 관리자가 직접 입력한다. 예매 일시는 비워도 저장되고, 일시가 없는 건은 유저 화면에 노출하지 않는다.
 */
@Entity
@Table(name = "concert_presales")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConcertPresale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 공연이 DB 단에서 지워질 때(공연 삭제, 아티스트 삭제 연쇄)도 함께 지워진다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Concert concert;

    @Column(name = "opens_at")
    private LocalDateTime opensAt;

    @Convert(converter = TicketVendorListConverter.class)
    @Column(name = "vendors", columnDefinition = "TEXT")
    private List<TicketVendorInfo> vendors;

    @Builder
    private ConcertPresale(Concert concert, LocalDateTime opensAt, List<TicketVendorInfo> vendors) {
        this.concert = concert;
        this.opensAt = opensAt;
        this.vendors = (vendors == null) ? List.of() : List.copyOf(vendors);
    }

    public void update(LocalDateTime opensAt, List<TicketVendorInfo> vendors) {
        this.opensAt = opensAt;
        this.vendors = (vendors == null) ? List.of() : List.copyOf(vendors);
    }
}