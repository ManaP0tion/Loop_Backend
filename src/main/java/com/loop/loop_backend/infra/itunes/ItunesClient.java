package com.loop.loop_backend.infra.itunes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * iTunes Search API 클라이언트 (AD-03). 아티스트 연결·곡 불러오기 공용.
 * 자체 DTO만 반환하고 도메인 엔티티는 모른다.
 * 응답 Content-Type이 text/javascript라 String으로 받아 직접 파싱한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ItunesClient {

    private static final String SEARCH_URL = "https://itunes.apple.com/search";
    private static final String LOOKUP_URL = "https://itunes.apple.com/lookup";
    // 곡명 원문은 JP 스토어. JP 스토어는 lang=en_us를 줘도 원문을 그대로 줘서,
    // 영문/로마자 곡명은 US 스토어에서 받는다 (trackId는 스토어 간 동일)
    public static final String COUNTRY_JP = "JP";
    public static final String COUNTRY_US = "US";
    private static final String LANG_JA = "ja_jp";
    private static final int TRACK_LIMIT = 200; // lookup 최대치

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** 아티스트 후보 검색 (관리자가 연결할 아티스트를 고르는 용도) */
    public List<ItunesArtist> searchArtists(String query) {
        URI uri = UriComponentsBuilder.fromHttpUrl(SEARCH_URL)
                .queryParam("term", query)
                .queryParam("entity", "musicArtist")
                .queryParam("country", COUNTRY_JP)
                .queryParam("lang", LANG_JA)
                .queryParam("limit", 20)
                .encode().build().toUri();
        return get(uri, new TypeReference<ItunesResponse<ItunesArtist>>() {}).results();
    }

    /** iTunes 아티스트 ID로 단건 조회. 없으면 ITUNES_ARTIST_NOT_FOUND */
    public ItunesArtist lookupArtist(Long itunesArtistId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(LOOKUP_URL)
                .queryParam("id", itunesArtistId)
                .queryParam("country", COUNTRY_JP)
                .encode().build().toUri();
        return get(uri, new TypeReference<ItunesResponse<ItunesArtist>>() {}).results().stream()
                .filter(a -> "artist".equals(a.wrapperType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ITUNES_ARTIST_NOT_FOUND));
    }

    /** 아티스트의 곡 목록 (응답 순서 유지). 첫 요소(아티스트)는 걸러낸다. 스토어에 없는 곡은 빠진다 */
    public List<ItunesTrack> fetchTracks(Long itunesArtistId, String country) {
        URI uri = UriComponentsBuilder.fromHttpUrl(LOOKUP_URL)
                .queryParam("id", itunesArtistId)
                .queryParam("entity", "song")
                .queryParam("country", country)
                .queryParam("limit", TRACK_LIMIT)
                .encode().build().toUri();
        return get(uri, new TypeReference<ItunesResponse<ItunesTrack>>() {}).results().stream()
                .filter(t -> "track".equals(t.wrapperType()))
                .toList();
    }

    private <T> ItunesResponse<T> get(URI uri, TypeReference<ItunesResponse<T>> type) {
        try {
            String body = restTemplate.getForObject(uri, String.class);
            if (body == null) throw new BusinessException(ErrorCode.ITUNES_FETCH_FAILED);
            ItunesResponse<T> res = objectMapper.readValue(body, type);
            return res.results() == null ? new ItunesResponse<>(0, List.of()) : res;
        } catch (RestClientException | JsonProcessingException e) {
            log.warn("[iTunes] 요청 실패 uri={} : {}", uri, e.getMessage());
            throw new BusinessException(ErrorCode.ITUNES_FETCH_FAILED);
        }
    }
}
