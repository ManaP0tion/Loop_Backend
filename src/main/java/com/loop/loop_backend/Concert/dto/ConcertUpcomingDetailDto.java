package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// 예정 공연 상세. from(Concert)는 아직 안 만듦 — Concert 엔티티에 예매/공연장 상세 필드가
// 생긴 뒤에 매핑을 붙일 예정 (지금은 응답 구조만 먼저 확정).
@Getter
@Builder
public class ConcertUpcomingDetailDto {

    private final Long concertId;
    private final Long artistId;
    private final String artistName;
    private final String title;
    private final String posterUrl;
    private final String venue; // 공연장 이름
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Integer dDay; // 오늘(KST) 기준 startDate까지 남은 일수. startDate 없으면 null

    // 예매정보
    private final Boolean presaleAvailable; // 선예매 유무
    private final LocalDate presaleDate; // 선예매 시작일
    private final LocalDate generalSaleDate; // 일반 예매 시작일
    private final List<TicketVendorInfo> ticketVendors; // 예매처 목록(이름+링크), 복수 가능

    // 공연장 정보
    private final String venueAddress; // 공연장 주소
    private final Integer venueCapacity; // 이 공연이 열리는 홀의 수용 인원
    private final Double venueLatitude; // 공연장 위도 - 지도 링크는 프론트에서 좌표로 생성
    private final Double venueLongitude; // 공연장 경도
    private final String seatingChartImageUrl; // 자리 배치도 이미지 URL (관리자 업로드)
}