package com.loop.loop_backend.Venue.service;

import com.loop.loop_backend.ConcertImport.kopis.KopisClient.KopisFacility;
import com.loop.loop_backend.Venue.domain.CityDistrict;
import com.loop.loop_backend.Venue.domain.Venue;
import com.loop.loop_backend.Venue.domain.VenueCapacity;
import com.loop.loop_backend.Venue.dto.VenueCsvImportResult;
import com.loop.loop_backend.Venue.dto.VenueRequestDto;
import com.loop.loop_backend.Venue.dto.VenueResponseDto;
import com.loop.loop_backend.Venue.dto.VenueUpdateRequestDto;
import com.loop.loop_backend.Venue.repository.VenueRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.util.WebUrls;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 공연장 관리(AD-02). 관리자 화면의 조회·등록·수정·삭제와,
 * KOPIS 공연 승인 시 공연장을 찾거나 KOPIS 값으로 새로 만드는 일을 맡는다.
 */
@Service
@RequiredArgsConstructor
public class VenueService {

    static final String[] CSV_HEADER = {"id", "name", "address", "capacity", "seatViewUrl", "kakaoMapUrl", "naverMapUrl",
            "lodgingUrl", "lodgingVisible", "kopisFacilityId", "kopisHallId"};
    // 숙소 노출 칸은 PM 시트와 같은 O/X로 쓴다
    private static final String CSV_ON = "O";
    private static final String CSV_OFF = "X";
    private static final char BOM = '﻿';
    private static final int TEXT_MAX_LENGTH = 255; // name, address 컬럼 길이

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
                .capacity(VenueCapacity.parse(req.capacity()))
                .seatViewUrl(blankToNull(req.seatViewUrl()))
                .kakaoMapUrl(blankToNull(req.kakaoMapUrl()))
                .naverMapUrl(blankToNull(req.naverMapUrl()))
                .build());
        venue.changeLodging(Boolean.TRUE.equals(req.lodgingVisible()), req.lodgingUrl());
        return VenueResponseDto.from(venue, 0L); // 방금 만든 공연장이라 연결된 공연이 없다
    }

    /**
     * 부분 수정 - 다른 PATCH API와 같은 규칙으로 null은 변경 없음. 링크·수용 인원은 빈 문자열("")로 보내면 비운다.
     * 필수값(이름·주소)을 빈 값으로 보낸 경우는 요청 검증에서 이미 400으로 막힌다.
     */
    @Transactional
    public VenueResponseDto update(Long id, VenueUpdateRequestDto req) {
        Venue venue = findVenue(id);
        venue.updateInfo(
                req.name() != null ? req.name().trim() : venue.getName(),
                req.address() != null ? req.address().trim() : venue.getAddress(),
                req.capacity() != null ? VenueCapacity.parse(req.capacity()) : venue.getCapacity(),
                patchLink(req.seatViewUrl(), venue.getSeatViewUrl()),
                patchLink(req.kakaoMapUrl(), venue.getKakaoMapUrl()),
                patchLink(req.naverMapUrl(), venue.getNaverMapUrl()));
        venue.patchLodging(req.lodgingVisible(), req.lodgingUrl());
        return VenueResponseDto.from(venue, venueRepository.countConcerts(id));
    }

    /** CSV 다운로드 - 전체 공연장, id 순. UTF-8 BOM(엑셀 한글 깨짐 방지). 업로드와 같은 헤더라 고쳐서 그대로 올리면 된다. */
    @Transactional(readOnly = true)
    public byte[] exportCsv() {
        StringWriter out = new StringWriter();
        out.write(BOM);
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setHeader(CSV_HEADER).build())) {
            for (Venue v : venueRepository.findAll(Sort.by("id"))) {
                printer.printRecord(v.getId(), v.getName(), v.getAddress(), v.getCapacity().toText(),
                        v.getSeatViewUrl(), v.getKakaoMapUrl(), v.getNaverMapUrl(),
                        v.getLodgingUrl(), v.isLodgingVisible() ? CSV_ON : CSV_OFF,
                        v.getKopisFacilityId(), v.getKopisHallId());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * CSV 일괄 수정 - id로 찾아 이름·주소·수용 인원·링크 3개·숙소를 바꾼다. 빈 칸은 기존 값 유지, KOPIS ID 칸은 무시.
     * 이름으로 찾지 않는다: 이름은 고칠 값이고, 같은 이름의 공연장도 있을 수 있다.
     * 찾지 못한 행·잘못된 값이 있는 행은 통째로 건너뛰고(일부 칸만 반영되지 않게) 행 번호로 알려준다.
     */
    @Transactional
    public VenueCsvImportResult importCsv(InputStream in) {
        List<CSVRecord> records = readCsv(in);
        // UTF-8이 아닌 파일(엑셀 기본 CSV 저장은 CP949)은 한글이 깨진 문자(U+FFFD)로 읽힌다.
        // 그대로 두면 깨진 이름이 저장되므로, 반영을 시작하기 전에 파일 전체를 거절한다.
        if (records.stream().flatMap(CSVRecord::stream).anyMatch(value -> value.indexOf('�') >= 0)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_FORMAT);
        }

        int updated = 0, unchanged = 0;
        List<Long> failedRows = new ArrayList<>();
        for (CSVRecord r : records) {
            Venue venue = findVenueForCsv(csvColumn(r, "id"));
            if (venue == null) {
                failedRows.add(r.getRecordNumber());
                continue;
            }
            String name, address, seatViewUrl, kakaoMapUrl, naverMapUrl, lodgingUrl;
            VenueCapacity capacity;
            boolean lodgingVisible;
            try {
                // 먼저 모든 칸을 검증해 한 칸만 잘못돼도 행 전체를 건너뛴다
                name = csvText(r, "name", venue.getName());
                address = csvText(r, "address", venue.getAddress());
                capacity = csvCapacity(r, venue.getCapacity());
                seatViewUrl = csvLink(r, "seatViewUrl", venue.getSeatViewUrl());
                kakaoMapUrl = csvLink(r, "kakaoMapUrl", venue.getKakaoMapUrl());
                naverMapUrl = csvLink(r, "naverMapUrl", venue.getNaverMapUrl());
                lodgingVisible = csvLodgingVisible(r, venue.isLodgingVisible());
                String lodgingUrlCell = csvColumn(r, "lodgingUrl");
                lodgingUrl = Venue.checkLodging(lodgingVisible,
                        lodgingUrlCell.isEmpty() ? venue.getLodgingUrl() : lodgingUrlCell);
            } catch (IllegalArgumentException e) {
                failedRows.add(r.getRecordNumber());
                continue;
            }
            // 받은 파일을 그대로 다시 올린 행은 '변경 없음'으로 센다
            if (name.equals(venue.getName()) && address.equals(venue.getAddress())
                    && Objects.equals(capacity, venue.getCapacity())
                    && Objects.equals(seatViewUrl, venue.getSeatViewUrl())
                    && Objects.equals(kakaoMapUrl, venue.getKakaoMapUrl())
                    && Objects.equals(naverMapUrl, venue.getNaverMapUrl())
                    && lodgingVisible == venue.isLodgingVisible()
                    && Objects.equals(lodgingUrl, venue.getLodgingUrl())) {
                unchanged++;
            } else {
                venue.updateInfo(name, address, capacity, seatViewUrl, kakaoMapUrl, naverMapUrl);
                venue.changeLodging(lodgingVisible, lodgingUrl);
                updated++;
            }
        }
        return new VenueCsvImportResult(updated, unchanged, failedRows);
    }

    /** 헤더 행 기준으로 읽는다(UTF-8, BOM 허용). 깨진 CSV(따옴표 짝 안 맞음, 헤더 중복 등)는 400. */
    private static List<CSVRecord> readCsv(InputStream in) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build();
        try (Reader reader = new InputStreamReader(
                BOMInputStream.builder().setInputStream(in).get(), StandardCharsets.UTF_8)) {
            return format.parse(reader).getRecords();
        } catch (IOException | IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_FORMAT);
        }
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
                .capacity(VenueCapacity.of(facility.capacity()))
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

    private Venue findVenueForCsv(String id) {
        if (id.isEmpty()) return null;
        try {
            return venueRepository.findById(Long.valueOf(id)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String csvColumn(CSVRecord r, String name) {
        return r.isMapped(name) && r.isSet(name) ? r.get(name) : "";
    }

    /** 이름·주소: 빈 칸이면 기존 값, 길이 초과면 IllegalArgumentException(행 실패). */
    private static String csvText(CSVRecord r, String column, String current) {
        String value = csvColumn(r, column);
        if (value.isEmpty()) return current;
        if (value.length() > TEXT_MAX_LENGTH) {
            throw new IllegalArgumentException(column + "은 " + TEXT_MAX_LENGTH + "자까지 입력할 수 있다");
        }
        return value;
    }

    /** 수용 인원: 빈 칸이면 기존 값, 숫자 또는 범위(VenueCapacity.parse - 형식 오류는 IllegalArgumentException). */
    private static VenueCapacity csvCapacity(CSVRecord r, VenueCapacity current) {
        String value = csvColumn(r, "capacity");
        return value.isEmpty() ? current : VenueCapacity.parse(value);
    }

    /** 숙소 노출: 빈 칸이면 기존 값, O(켬)/X(끔)만(대소문자 무관). 그 외 값은 IllegalArgumentException(행 실패). */
    private static boolean csvLodgingVisible(CSVRecord r, boolean current) {
        String value = csvColumn(r, "lodgingVisible");
        if (value.isEmpty()) return current;
        if (value.equalsIgnoreCase(CSV_ON)) return true;
        if (value.equalsIgnoreCase(CSV_OFF)) return false;
        throw new IllegalArgumentException("숙소 노출은 O 또는 X다: " + value);
    }

    /** 링크: 빈 칸이면 기존 값, http/https 주소만(WebUrls). */
    private static String csvLink(CSVRecord r, String column, String current) {
        String value = csvColumn(r, column);
        return value.isEmpty() ? current : WebUrls.normalize(value);
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