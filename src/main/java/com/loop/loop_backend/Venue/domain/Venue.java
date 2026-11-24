package com.loop.loop_backend.Venue.domain;

import com.loop.loop_backend.common.util.WebUrls;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 공연장(AD-02). 한 번 등록하면 여러 공연에서 골라 쓰고, 수정하면 연결된 공연에 그대로 반영된다.
 *
 * 수용 인원·좌석 시야는 홀마다 다르므로 공연장은 홀 단위로 등록한다. KOPIS 공연과는 시설·홀 ID로 연결한다 -
 * 이름은 관리자가 고쳐 쓰는 표시용이라 연결 키로 쓰지 않고, 같은 이름의 공연장도 허용한다.
 * (시설 ID, 홀 ID)는 한 공연장에만 연결된다 - KOPIS 자동 생성이 같은 시설·홀의 공연장을 먼저 찾아 쓰고,
 * 관리자 등록·수정으로는 KOPIS ID를 넣거나 바꿀 수 없다. (홀 ID가 없는 경우는 DB 유니크가 NULL을 구분하지 못해 조회로 막는다)
 */
@Entity
@Table(
        name = "venues",
        uniqueConstraints = @UniqueConstraint(columnNames = {"kopis_facility_id", "kopis_hall_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

    public static final int LODGING_URL_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // 시·구 단위까지만 둔다. 상세 주소는 지도 링크로 넘긴 길찾기 앱에서 확인한다.
    @Column(name = "address", nullable = false, length = 255)
    private String address;

    // 수용 인원(VenueCapacity). 범위면 최솟값·최댓값, 단일 값이면 최댓값만 있다. 모르면 둘 다 비운다.
    // 범위가 생기기 전 컬럼(capacity)을 최댓값으로 그대로 쓴다 - 기존 값은 단일 값으로 읽힌다.
    @Column(name = "capacity_min")
    private Integer capacityMin;

    @Column(name = "capacity")
    private Integer capacityMax;

    // 좌석 시야(오프메이트) 외부 링크. 비우면 화면에서 해당 행을 숨긴다.
    @Column(name = "seat_view_url", length = 500)
    private String seatViewUrl;

    @Column(name = "kakao_map_url", length = 500)
    private String kakaoMapUrl;

    @Column(name = "naver_map_url", length = 500)
    private String naverMapUrl;

    // 숙소 섹션 노출 여부와 딥링크. 같은 공연장의 공연은 숙소도 같아서 공연이 아닌 공연장에 둔다 -
    // 공연 수정 화면에서 바꿔도 이 공연장의 값이 바뀌어 같은 공연장의 다른 공연에도 반영된다.
    // 켜려면 딥링크가 있어야 한다. 기존 공연장은 Off.
    @Column(name = "lodging_visible", nullable = false, columnDefinition = "boolean default false")
    private boolean lodgingVisible;

    // 완성된 딥링크를 그대로 저장한다(자동 생성 없음). 제휴 딥링크는 길어서 다른 링크(500자)보다 길게 둔다.
    @Column(name = "lodging_url", length = LODGING_URL_MAX_LENGTH)
    private String lodgingUrl;

    // 좌표. KOPIS 공연 승인으로 자동 생성될 때 KOPIS 시설 좌표가 들어간다.
    // 지도 링크(카카오/네이버)를 관리자가 넣기 전에는 프론트가 이 좌표로 링크를 만든다.
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // KOPIS 시설 ID(mt10id). 승인 시 자동 생성될 때만 채워지고 관리자 수정으로는 바뀌지 않는다.
    // 관리자가 직접 만든 공연장이면 비어 있다.
    @Column(name = "kopis_facility_id", length = 20)
    private String kopisFacilityId;

    // KOPIS 홀 ID(mt13id). 시설 ID 없이 홀 ID만 있을 수는 없다.
    @Column(name = "kopis_hall_id", length = 20)
    private String kopisHallId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Venue(String name, String address, VenueCapacity capacity, String seatViewUrl,
                  String kakaoMapUrl, String naverMapUrl, Double latitude, Double longitude,
                  String kopisFacilityId, String kopisHallId) {
        this.name = name;
        this.address = address;
        changeCapacity(capacity);
        this.seatViewUrl = seatViewUrl;
        this.kakaoMapUrl = kakaoMapUrl;
        this.naverMapUrl = naverMapUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.kopisFacilityId = kopisFacilityId;
        this.kopisHallId = kopisHallId;
    }

    /**
     * 관리자 화면에서 고치는 정보만 바꾼다. 좌표와 KOPIS 시설·홀 ID는 KOPIS 자동 생성 때만 채워지는 값이라
     * 여기서 바꾸지 않는다 - 화면에 없는 값이 수정 때 지워지거나 바뀌지 않게 하려는 것.
     */
    public void updateInfo(String name, String address, VenueCapacity capacity,
                           String seatViewUrl, String kakaoMapUrl, String naverMapUrl) {
        this.name = name;
        this.address = address;
        changeCapacity(capacity);
        this.seatViewUrl = seatViewUrl;
        this.kakaoMapUrl = kakaoMapUrl;
        this.naverMapUrl = naverMapUrl;
    }

    /**
     * 숙소 노출 여부와 딥링크를 함께 바꾼다(하나만 바꿀 때는 다른 하나에 현재 값을 넘긴다).
     * 딥링크는 빈 값이면 비우고, http/https 주소만 받는다. 노출을 켰는데 딥링크가 없으면 400 -
     * 둘을 한 번에 검사해야 "링크를 넣으면서 켜기", "끄면서 링크 지우기"가 순서와 상관없이 된다.
     */
    public void changeLodging(boolean visible, String url) {
        String normalized = checkLodging(visible, url);
        this.lodgingVisible = visible;
        this.lodgingUrl = normalized;
    }

    /**
     * 숙소 부분 수정(PATCH 규칙) - null은 변경 없음, 딥링크 ""는 비움. 둘 다 null이면 아무것도 하지 않는다.
     * 공연장 수정 화면과 공연 수정 화면이 같이 쓴다.
     */
    public void patchLodging(Boolean visible, String url) {
        if (visible == null && url == null) return;
        changeLodging(visible != null ? visible : lodgingVisible, url != null ? url : lodgingUrl);
    }

    /**
     * 숙소 값을 바꾸지 않고 검사만 한다 - 정리된 딥링크(빈 값이면 null)를 돌려주고, 규칙 위반은 IllegalArgumentException.
     * CSV처럼 행의 모든 칸을 먼저 검사한 뒤 반영해야 하는 곳에서 쓴다.
     */
    public static String checkLodging(boolean visible, String url) {
        String normalized = WebUrls.normalize(url, LODGING_URL_MAX_LENGTH);
        if (visible && normalized == null) {
            throw new IllegalArgumentException("숙소 노출을 켜려면 딥링크가 있어야 한다");
        }
        return normalized;
    }

    /** 사용자 화면에 나갈 숙소 딥링크. 노출이 꺼져 있으면 null(섹션을 그리지 않는다). */
    public String visibleLodgingUrl() {
        return lodgingVisible ? lodgingUrl : null;
    }

    public VenueCapacity getCapacity() {
        return new VenueCapacity(capacityMin, capacityMax);
    }

    private void changeCapacity(VenueCapacity capacity) {
        VenueCapacity value = capacity == null ? VenueCapacity.EMPTY : capacity;
        this.capacityMin = value.min();
        this.capacityMax = value.max();
    }
}
