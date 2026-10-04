package com.loop.loop_backend.Venue.domain;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // 시·구 단위까지만 둔다. 상세 주소는 지도 링크로 넘긴 길찾기 앱에서 확인한다.
    @Column(name = "address", nullable = false, length = 255)
    private String address;

    // 공연장 기준 평균 수용 인원. 모르면 비운다.
    @Column(name = "capacity")
    private Integer capacity;

    // 좌석 시야(자리어때) 외부 링크. 비우면 화면에서 해당 행을 숨긴다.
    @Column(name = "seat_view_url", length = 500)
    private String seatViewUrl;

    @Column(name = "kakao_map_url", length = 500)
    private String kakaoMapUrl;

    @Column(name = "naver_map_url", length = 500)
    private String naverMapUrl;

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
    private Venue(String name, String address, Integer capacity, String seatViewUrl,
                  String kakaoMapUrl, String naverMapUrl, Double latitude, Double longitude,
                  String kopisFacilityId, String kopisHallId) {
        this.name = name;
        this.address = address;
        this.capacity = capacity;
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
    public void updateInfo(String name, String address, Integer capacity,
                           String seatViewUrl, String kakaoMapUrl, String naverMapUrl) {
        this.name = name;
        this.address = address;
        this.capacity = capacity;
        this.seatViewUrl = seatViewUrl;
        this.kakaoMapUrl = kakaoMapUrl;
        this.naverMapUrl = naverMapUrl;
    }
}