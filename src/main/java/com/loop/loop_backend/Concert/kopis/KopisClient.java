package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
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
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class KopisClient {

    @Value("${kopis.api-key}")
    private String apiKey;

    private static final String BASE_URL = "http://kopis.or.kr/openApi/restful/pblprfr";
    private static final String DETAIL_URL = BASE_URL; // 공연 상세는 목록과 같은 경로 뒤에 /{공연ID}
    private static final String FACILITY_URL = "http://kopis.or.kr/openApi/restful/prfplc"; // 공연시설 상세 /{시설ID}
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
        return parseCastFromDetail(fetchXml(DETAIL_URL, kopisId));
    }

    /**
     * 승인 시점 상세 조회: 가격/공연시간/예매처 목록, 그리고 공연장 조회에 쓸 시설·홀 ID.
     * 조회·파싱에 실패하면 모든 필드가 null. (예매처가 하나도 없는 정상 응답은 null이 아니라 빈 목록)
     */
    public KopisDetail getPerformanceDetail(String kopisId) {
        String xml = fetchXml(DETAIL_URL, kopisId);
        if (xml == null || xml.isBlank()) return KopisDetail.EMPTY;
        try {
            NodeList items = buildDocument(xml).getElementsByTagName("db");
            if (items.getLength() == 0) return KopisDetail.EMPTY;
            Element db = (Element) items.item(0);
            return new KopisDetail(text(db, "pcseguidance"), text(db, "dtguidance"),
                    parseTicketVendors(db), text(db, "mt10id"), text(db, "mt13id"));
        } catch (Exception e) {
            log.warn("Failed to parse KOPIS detail for {}: {}", kopisId, e.getMessage());
            return KopisDetail.EMPTY;
        }
    }

    /**
     * @param facilityId 공연장(시설) ID(mt10id) - getFacility 호출에 쓴다
     * @param hallId     시설 안에서 이 공연이 열리는 홀 ID(mt13id) - 홀별 수용인원을 찾는 데 쓴다
     */
    public record KopisDetail(String price, String showtime, List<TicketVendorInfo> ticketVendors,
                              String facilityId, String hallId) {

        static final KopisDetail EMPTY = new KopisDetail(null, null, null, null, null);

        /** 어드민 화면/DTO가 아직 단일 예매처 URL(ticketUrl)을 쓰고 있어서, 목록의 첫 링크를 돌려준다. */
        public String ticketUrl() {
            if (ticketVendors == null) return null;
            return ticketVendors.stream().map(TicketVendorInfo::url).filter(Objects::nonNull).findFirst().orElse(null);
        }
    }

    /**
     * 공연장(시설) 조회: 주소/좌표/수용인원. 조회·파싱에 실패하면 모든 필드가 null.
     * 수용인원은 hallId와 일치하는 홀의 값을 쓰고, 홀 정보가 없으면 시설 전체 수용인원으로 대신한다.
     */
    public KopisFacility getFacility(String facilityId, String hallId) {
        if (facilityId == null) return KopisFacility.EMPTY;
        String xml = fetchXml(FACILITY_URL, facilityId);
        if (xml == null || xml.isBlank()) return KopisFacility.EMPTY;
        try {
            NodeList items = buildDocument(xml).getElementsByTagName("db");
            if (items.getLength() == 0) return KopisFacility.EMPTY;
            Element db = (Element) items.item(0);
            return new KopisFacility(childText(db, "adres"),
                    parseDouble(childText(db, "la")), parseDouble(childText(db, "lo")),
                    hallCapacity(db, hallId));
        } catch (Exception e) {
            log.warn("Failed to parse KOPIS facility for {}: {}", facilityId, e.getMessage());
            return KopisFacility.EMPTY;
        }
    }

    public record KopisFacility(String address, Double latitude, Double longitude, Integer capacity) {
        static final KopisFacility EMPTY = new KopisFacility(null, null, null, null);
    }

    private String fetchXml(String baseUrl, String id) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl + "/" + id)
                .queryParam("service", apiKey)
                .encode()
                .build()
                .toUri();
        try {
            return restTemplate.getForObject(uri, String.class);
        } catch (Exception e) {
            log.warn("Failed to fetch {} from KOPIS: {}", id, e.getMessage());
            return null;
        }
    }

    /** relates의 relate마다 (예매처명, 링크) 한 쌍. 링크가 없는 항목은 건너뛴다. 응답에 나온 순서를 유지한다. */
    private List<TicketVendorInfo> parseTicketVendors(Element db) {
        List<TicketVendorInfo> vendors = new ArrayList<>();
        NodeList relates = db.getElementsByTagName("relate");
        for (int i = 0; i < relates.getLength(); i++) {
            Element relate = (Element) relates.item(i);
            String url = text(relate, "relateurl");
            if (url == null) continue;
            vendors.add(new TicketVendorInfo(text(relate, "relatenm"), url));
        }
        return vendors;
    }

    /**
     * 시설 안의 mt13 중 hallId가 일치하는 홀의 수용인원. 홀을 찾았는데 값이 0/공백이면 "모름"이라 null로 둔다
     * (0석짜리 공간이 시설 전체 인원으로 잘못 보이지 않게). 홀 자체를 못 찾았을 때만 시설 전체 수용인원을 쓴다.
     */
    private Integer hallCapacity(Element facility, String hallId) {
        if (hallId != null) {
            NodeList halls = facility.getElementsByTagName("mt13");
            for (int i = 0; i < halls.getLength(); i++) {
                Element hall = (Element) halls.item(i);
                if (hallId.equals(childText(hall, "mt13id"))) {
                    return parseSeatCount(childText(hall, "seatscale"));
                }
            }
        }
        return parseSeatCount(childText(facility, "seatscale"));
    }

    /** "14,483"처럼 천 단위 쉼표가 섞여 오기도 한다. 숫자가 아니거나 0 이하면 null. */
    private Integer parseSeatCount(String raw) {
        if (raw == null) return null;
        try {
            int count = Integer.parseInt(raw.replace(",", "").trim());
            return count > 0 ? count : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String raw) {
        if (raw == null) return null;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 바로 아래 자식 태그의 텍스트만 읽는다. text()는 후손 전체에서 첫 태그를 찾는데,
     * 시설 응답은 시설 전체 seatscale과 홀별 seatscale이 같은 이름이라 순서에 기대면 엉뚱한 값을 읽을 수 있다.
     */
    private String childText(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && tag.equals(child.getTagName())) {
                String value = child.getTextContent();
                return (value == null || value.isBlank()) ? null : value.trim();
            }
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
