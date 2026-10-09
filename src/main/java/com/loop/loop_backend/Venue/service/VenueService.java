package com.loop.loop_backend.Venue.service;

import com.loop.loop_backend.ConcertImport.kopis.KopisClient.KopisFacility;
import com.loop.loop_backend.Venue.domain.CityDistrict;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.dto.VenueRequestDto;
import com.loop.loop_backend.Venue.dto.VenueResponseDto;
import com.loop.loop_backend.Venue.dto.VenueUpdateRequestDto;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 공연장 관리(AD-02). 관리자 화면의 조회·등록·수정·삭제와,
 * KOPIS 공연 승인 시 공연장을 찾거나 KOPIS 값으로 새로 만드는 일을 맡는다.
 */
@Service
@RequiredArgsConstructor
public class VenueService {

    private final VenueRepository venueRepository;

    /** 목록의 공연 수는 페이지의 공연장들을 한 번에 센다 - 공연장마다 세면 공연장 수만큼 쿼리가 늘어난다(N+1). */
    @Transactional(readOnly = true)
    public Page<VenueResponseDto> search(String q, Pageable pageable) {
        String keyword = blankToNull(q);
        Page<Venue> page = (keyword == null)
                ? venueRepository.findAll(pageable)
                : venueRepository.findByNameContainingIgnoreCase(keyword, pageable);
        Map<Long, Long> concertCounts = countConcerts(page.getContent());
        return page.map(venue -> VenueResponseDto.from(venue, concertCounts.getOrDefault(venue.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public VenueResponseDto get(Long id) {
        Venue venue = findVenue(id);
        return VenueResponseDto.from(venue, venueRepository.countConcerts(id));
    }

    /** 직접 등록 - 좌표와 KOPIS 시설·홀 ID 없이 만든다(그 값들은 KOPIS 자동 생성 때만 채워진다). */
    @Transactional
    public VenueResponseDto create(VenueRequestDto req) {
        Venue venue = venueRepository.save(Venue.builder()
                .name(req.name().trim())
                .address(req.address().trim())
                .capacity(req.capacity())
                .seatViewUrl(blankToNull(req.seatViewUrl()))
                .kakaoMapUrl(blankToNull(req.kakaoMapUrl()))
                .naverMapUrl(blankToNull(req.naverMapUrl()))
                .build());
        return VenueResponseDto.from(venue, 0L); // 방금 만든 공연장이라 연결된 공연이 없다
    }

    /**
     * 부분 수정 - 다른 PATCH API와 같은 규칙으로 null은 변경 없음. 링크는 빈 문자열("")로 보내면 비운다.
     * 필수값(이름·주소)을 빈 값으로 보낸 경우는 요청 검증에서 이미 400으로 막힌다. 수용 인원은 비울 수 없다.
     */
    @Transactional
    public VenueResponseDto update(Long id, VenueUpdateRequestDto req) {
        Venue venue = findVenue(id);
        venue.updateInfo(
                req.name() != null ? req.name().trim() : venue.getName(),
                req.address() != null ? req.address().trim() : venue.getAddress(),
                req.capacity() != null ? req.capacity() : venue.getCapacity(),
                patchLink(req.seatViewUrl(), venue.getSeatViewUrl()),
                patchLink(req.kakaoMapUrl(), venue.getKakaoMapUrl()),
                patchLink(req.naverMapUrl(), venue.getNaverMapUrl()));
        return VenueResponseDto.from(venue, venueRepository.countConcerts(id));
    }

    /** 연결된 공연이 있어도 지운다 - 그 공연들의 공연장 연결은 DB가 비운다(ON DELETE SET NULL). */
    @Transactional
    public void delete(Long id) {
        venueRepository.delete(findVenue(id));
    }

    /**
     * KOPIS 공연 승인 시 공연장: 같은 KOPIS 시설·홀로 등록된 공연장이 있으면 그것을, 없으면 KOPIS 값으로 새로 만든다.
     * 이름은 "시설명 (홀명)", 주소는 시·구까지만, 수용 인원은 홀 좌석 수, 좌표는 시설 좌표 -
     * 좌석 시야·지도 링크는 관리자가 공연장 탭에서 채운다(그 전까지 지도는 좌표로 연결).
     * KOPIS 조회에 실패해 이름·주소를 알 수 없으면 만들지 않고 null을 돌려준다 (승인은 공연장 없이 진행).
     */
    @Transactional
    public Venue findOrCreateFromKopis(String facilityId, String hallId, KopisFacility facility) {
        if (facilityId == null) return null;
        Optional<Venue> existing = venueRepository.findByKopisFacilityIdAndKopisHallId(facilityId, hallId);
        if (existing.isPresent()) return existing.get();

        String name = kopisVenueName(facility);
        String address = CityDistrict.from(facility.address());
        if (name == null || address == null) return null;

        return venueRepository.save(Venue.builder()
                .name(name)
                .address(address)
                .capacity(facility.capacity())
                .latitude(facility.latitude())
                .longitude(facility.longitude())
                .kopisFacilityId(facilityId)
                .kopisHallId(hallId)
                .build());
    }

    private Map<Long, Long> countConcerts(List<Venue> venues) {
        if (venues.isEmpty()) return Map.of();
        List<Long> ids = venues.stream().map(Venue::getId).toList();
        return venueRepository.countConcertsByVenueIds(ids).stream()
                .collect(Collectors.toMap(VenueRepository.VenueConcertCount::getVenueId,
                        VenueRepository.VenueConcertCount::getConcertCount));
    }

    private Venue findVenue(Long id) {
        return venueRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VENUE_NOT_FOUND));
    }

    /** 링크 부분 수정: null이면 기존 값 유지, 빈 문자열이면 비움, 그 외에는 앞뒤 공백을 지운 값으로 변경. */
    private static String patchLink(String sent, String current) {
        return sent == null ? current : blankToNull(sent);
    }

    private static String kopisVenueName(KopisFacility facility) {
        String name = blankToNull(facility.name());
        if (name == null) return null;
        String hallName = blankToNull(facility.hallName());
        return hallName == null ? name : name + " (" + hallName + ")";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}