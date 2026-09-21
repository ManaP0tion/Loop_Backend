package com.loop.loop_backend.Concert.kopis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class KopisClient {

    @Value("${kopis.api-key}")
    private String apiKey;

    private static final String BASE_URL = "http://kopis.or.kr/openApi/restful/pblprfr";
    private static final DateTimeFormatter PARAM_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter PARSE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final RestTemplate restTemplate;

    /** 타이틀 키워드 없이 대중음악 전체 공연을 페이지네이션으로 수집한다. */
    public List<KopisPerformance> getAllUpcomingPerformances() {
        String stdate = LocalDate.now().format(PARAM_FORMAT);
        String eddate = LocalDate.now().plusYears(1).format(PARAM_FORMAT);
        List<KopisPerformance> all = new ArrayList<>();
        int page = 1;

        while (true) {
            URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .queryParam("service", apiKey)
                    .queryParam("stdate", stdate)
                    .queryParam("eddate", eddate)
                    .queryParam("rows", 100)
                    .queryParam("cpage", page)
                    .encode()
                    .build()
                    .toUri();

            try {
                String xml = restTemplate.getForObject(uri, String.class);
                ParsedPage parsed = parseListXml(xml);
                all.addAll(parsed.filtered);
                if (parsed.rawCount < 100) break;
                page++;
            } catch (Exception e) {
                log.warn("KOPIS full scan failed at page {}: {}", page, e.getMessage());
                break;
            }
        }

        log.info("KOPIS full scan: fetched {} performances (pages: {})", all.size(), page);
        return all;
    }

    /** 공연 상세의 출연진(prfcast) 문자열을 반환. 조회 실패 시 null. */
    public String getPerformanceCast(String kopisId) {
        return parseCastFromDetail(fetchDetailXml(kopisId));
    }

    /** 승인 시점 상세 조회: 가격/예매처URL/공연시간. 조회·파싱 실패 시 모든 필드 null. */
    public KopisDetail getPerformanceDetail(String kopisId) {
        String xml = fetchDetailXml(kopisId);
        if (xml == null || xml.isBlank()) return new KopisDetail(null, null, null);
        try {
            NodeList items = buildDocument(xml).getElementsByTagName("db");
            if (items.getLength() == 0) return new KopisDetail(null, null, null);
            Element db = (Element) items.item(0);
            return new KopisDetail(text(db, "pcseguidance"), firstRelateUrl(db), text(db, "dtguidance"));
        } catch (Exception e) {
            log.warn("Failed to parse KOPIS detail for {}: {}", kopisId, e.getMessage());
            return new KopisDetail(null, null, null);
        }
    }

    public record KopisDetail(String price, String ticketUrl, String showtime) {}

    private String fetchDetailXml(String kopisId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/" + kopisId)
                .queryParam("service", apiKey)
                .encode()
                .build()
                .toUri();
        try {
            return restTemplate.getForObject(uri, String.class);
        } catch (Exception e) {
            log.warn("Failed to fetch detail for {}: {}", kopisId, e.getMessage());
            return null;
        }
    }

    /** relate 목록 중 첫 유효 예매처 URL. ponytail: 첫 항목만 — 여러 예매처 다 필요하면 목록으로 확장. */
    private String firstRelateUrl(Element db) {
        NodeList relates = db.getElementsByTagName("relate");
        for (int i = 0; i < relates.getLength(); i++) {
            String url = text((Element) relates.item(i), "relateurl");
            if (url != null) return url;
        }
        return null;
    }

    /**
     * KOPIS 목록 XML을 파싱한다. rawCount는 페이지네이션 종료 조건(다음 페이지 존재 여부)
     * 판단용으로 XML의 db 태그 총 개수, filtered는 대중음악 장르만 남긴 결과.
     */
    private ParsedPage parseListXml(String xml) {
        if (xml == null || xml.isBlank()) return new ParsedPage(0, List.of());

        try {
            Document doc = buildDocument(xml);
            NodeList items = doc.getElementsByTagName("db");
            int rawCount = items.getLength();
            List<KopisPerformance> results = new ArrayList<>();

            for (int i = 0; i < rawCount; i++) {
                Element item = (Element) items.item(i);

                String kopisId = text(item, "mt20id");
                String title = text(item, "prfnm");
                String genre = text(item, "genrenm");
                if (kopisId == null || title == null) continue;
                if (!"대중음악".equals(genre)) continue;

                results.add(KopisPerformance.builder()
                        .kopisId(kopisId)
                        .title(title)
                        .posterUrl(toHttps(text(item, "poster")))
                        .venue(text(item, "fcltynm"))
                        .startDate(parseDate(text(item, "prfpdfrom")))
                        .endDate(parseDate(text(item, "prfpdto")))
                        .build());
            }
            return new ParsedPage(rawCount, results);
        } catch (Exception e) {
            log.warn("Failed to parse KOPIS list XML: {}", e.getMessage());
            return new ParsedPage(0, List.of());
        }
    }

    private record ParsedPage(int rawCount, List<KopisPerformance> filtered) {}

    private String parseCastFromDetail(String xml) {
        if (xml == null || xml.isBlank()) return null;
        try {
            Document doc = buildDocument(xml);
            NodeList items = doc.getElementsByTagName("db");
            if (items.getLength() == 0) return null;
            return text((Element) items.item(0), "prfcast");
        } catch (Exception e) {
            log.warn("Failed to parse KOPIS detail XML: {}", e.getMessage());
            return null;
        }
    }

    private Document buildDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private String text(Element element, String tag) {
        NodeList nodes = element.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        String value = nodes.item(0).getTextContent();
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            return LocalDate.parse(dateStr, PARSE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private String toHttps(String url) {
        if (url == null) return null;
        return url.startsWith("http://") ? "https://" + url.substring(7) : url;
    }
}
