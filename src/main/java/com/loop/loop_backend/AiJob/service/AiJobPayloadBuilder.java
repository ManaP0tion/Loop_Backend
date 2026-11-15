package com.loop.loop_backend.AiJob.service;

import com.loop.loop_backend.AiJob.domain.AiJobType;
import com.loop.loop_backend.AiJob.dto.payload.AiArtistPayload;
import com.loop.loop_backend.AiJob.dto.payload.ConcertEnrichPayload;
import com.loop.loop_backend.AiJob.dto.payload.FestivalLineupPayload;
import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 공연 DB 값으로 AI 서버 API 요청 본문(payload)을 만든다. 형식은 연동 약속 2절.
 * 대상 공연이 그 작업에 맞는지(페스티벌 여부·시작일)는 서비스가 먼저 확인한다.
 */
@Component
public class AiJobPayloadBuilder {

    public Object build(AiJobType type, Concert concert) {
        return switch (type) {
            case CONCERT_ENRICH -> concertEnrich(concert);
            case FESTIVAL_LINEUP -> festivalLineup(concert);
            case SETLIST_ACTUAL -> throw new BusinessException(ErrorCode.AI_JOB_UNSUPPORTED_TYPE);
        };
    }

    private ConcertEnrichPayload concertEnrich(Concert concert) {
        return new ConcertEnrichPayload(
                concert.getId(),
                concert.getKopisId(),
                concert.getTitle(),
                koreanFirst(concert.getTitleAliases()),
                concert.getCategory(),
                artists(concert.getArtist()),
                concert.getStartDate(),
                concert.getEndDate(),
                venueName(concert),
                vendorNames(concert.getTicketVendors()));
    }

    private FestivalLineupPayload festivalLineup(Concert concert) {
        return new FestivalLineupPayload(
                concert.getId(),
                concert.getTitle(),
                koreanFirst(concert.getTitleAliases()),
                concert.getStartDate(),
                concert.getEndDate(),
                venueName(concert));
    }

    /**
     * AI는 별칭의 첫 값을 한국어 공연명으로 검색어에 쓴다. 우리 별칭은 검색용 목록이라 어느 것이 한국어인지·순서가
     * 정해져 있지 않으므로, 한글이 들어간 별칭을 앞으로 보내고 나머지는 원래 순서를 지킨다(안정 정렬).
     * 한글 별칭이 없으면 첫 값은 한국어가 아니다.
     */
    static List<String> koreanFirst(List<String> aliases) {
        if (aliases == null) return List.of();
        return aliases.stream()
                .filter(alias -> alias != null && !alias.isBlank())
                .sorted(Comparator.comparing(alias -> !containsHangul(alias)))
                .toList();
    }

    private static boolean containsHangul(String text) {
        return text.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HANGUL);
    }

    // 공연에는 대표 아티스트가 한 명만 있다. 아티스트가 없는 페스티벌은 빈 목록(공연명으로 검색한다).
    private static List<AiArtistPayload> artists(Artist artist) {
        if (artist == null) return List.of();
        return List.of(new AiArtistPayload(artist.getName(), artist.getBaseName(), artist.getNameKo(), artist.getNameAlias()));
    }

    // 공연장 관리에 연결된 공연장 이름을 먼저, 없으면 KOPIS에서 받은 공연장 문자열
    private static String venueName(Concert concert) {
        if (concert.getLinkedVenue() != null && hasText(concert.getLinkedVenue().getName())) {
            return concert.getLinkedVenue().getName();
        }
        return hasText(concert.getVenue()) ? concert.getVenue() : null;
    }

    private static List<String> vendorNames(List<TicketVendorInfo> vendors) {
        if (vendors == null) return List.of();
        return vendors.stream()
                .filter(Objects::nonNull)
                .map(TicketVendorInfo::name)
                .filter(AiJobPayloadBuilder::hasText)
                .distinct()
                .toList();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}