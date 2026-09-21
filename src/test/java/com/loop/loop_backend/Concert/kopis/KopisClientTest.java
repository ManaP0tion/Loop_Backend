package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 승인 시점에 쓰는 KOPIS 상세/시설 조회가 요구사항대로 값을 뽑는지 검증한다.
// 기대값은 KOPIS가 실제로 내려준 응답(공연 PF287093, 시설 FC003670)에서 가져왔다.
class KopisClientTest {

    // 실제 공연 상세 응답 (Vaundy ASIA ARENA TOUR HORO IN SEOUL)
    private static final String DETAIL_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?><dbs><db><mt20id>PF287093</mt20id><mt13id>FC003670-01</mt13id> <prfnm>Vaundy ASIA ARENA TOUR HORO IN SEOUL</prfnm><prfpdfrom>2026.09.19</prfpdfrom><prfpdto>2026.09.20</prfpdto> <fcltynm>인스파이어 엔터테인먼트 리조트 (아레나)</fcltynm> <prfcast> </prfcast> <prfruntime>2시간</prfruntime> <prfage>만 7세 이상</prfage> <pcseguidance>스탠딩석 165,000원, R석 165,000원, S석 154,000원</pcseguidance> <poster>http://www.kopis.or.kr/upload/pfmPoster/PF_PF287093_260313_172418.gif </poster> <sty> </sty> <area>인천광역시</area> <genrenm>대중음악</genrenm> <prfstate>공연예정</prfstate><mt10id>FC003670</mt10id> <dtguidance>토요일(17:00), 일요일(16:00)</dtguidance><relates>  <relate>   <relatenm>놀유니버스</relatenm>   <relateurl>http://ticket.interpark.com/Ticket/Goods/GoodsInfo.asp?GoodsCode=26003199 </relateurl> </relate></relates></db></dbs>""";

    // 실제 시설 응답 (인스파이어 엔터테인먼트 리조트). 시설 전체 18,483석, 홀별로 14,483 / 0 / 3,000.
    private static final String FACILITY_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?><dbs><db><fcltynm>인스파이어 엔터테인먼트 리조트</fcltynm><mt10id>FC003670</mt10id><mt13cnt>3</mt13cnt><seatscale>18483</seatscale><telno> </telno><relateurl> </relateurl><adres>인천광역시 중구 공항문화로 127 (운서동)</adres><la>37.465530100000000</la><lo>126.38911770000000</lo><mt13s><mt13><prfplcnm>아레나</prfplcnm><mt13id>FC003670-01</mt13id><seatscale>14,483</seatscale></mt13><mt13><prfplcnm>디스커버리 파크</prfplcnm><mt13id>FC003670-02</mt13id><seatscale>0</seatscale></mt13><mt13><prfplcnm>인스파이어 볼룸</prfplcnm><mt13id>FC003670-03</mt13id><seatscale>3,000</seatscale></mt13></mt13s></db></dbs>""";

    private RestTemplate restTemplate;
    private KopisClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new KopisClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
    }

    private void givenResponse(String xml) {
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(xml);
    }

    private void givenFailure() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenThrow(new RestClientException("boom"));
    }

    // ---------- 공연 상세 ----------

    @Test
    void 상세조회는_가격_공연시간_시설ID_홀ID를_읽는다() {
        givenResponse(DETAIL_XML);

        KopisClient.KopisDetail detail = client.getPerformanceDetail("PF287093");

        assertThat(detail.price()).isEqualTo("스탠딩석 165,000원, R석 165,000원, S석 154,000원");
        assertThat(detail.showtime()).isEqualTo("토요일(17:00), 일요일(16:00)");
        assertThat(detail.facilityId()).isEqualTo("FC003670");
        assertThat(detail.hallId()).isEqualTo("FC003670-01");
    }

    @Test
    void 상세조회는_공연상세_경로에_공연ID로_요청한다() {
        givenResponse(DETAIL_XML);

        client.getPerformanceDetail("PF287093");

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uri.capture(), eq(String.class));
        assertThat(uri.getValue().getPath()).isEqualTo("/openApi/restful/pblprfr/PF287093");
    }

    @Test
    void 예매처가_있으면_이름과_링크를_읽고_링크_앞뒤_공백은_제거한다() {
        givenResponse(DETAIL_XML);

        KopisClient.KopisDetail detail = client.getPerformanceDetail("PF287093");

        assertThat(detail.ticketVendors()).containsExactly(new TicketVendorInfo("놀유니버스",
                "http://ticket.interpark.com/Ticket/Goods/GoodsInfo.asp?GoodsCode=26003199"));
        assertThat(detail.ticketUrl())
                .isEqualTo("http://ticket.interpark.com/Ticket/Goods/GoodsInfo.asp?GoodsCode=26003199");
    }

    @Test
    void 예매처가_여러_곳이면_응답_순서대로_모두_읽고_링크_없는_항목은_건너뛴다() {
        givenResponse("""
                <dbs><db><mt10id>FC1</mt10id><relates>
                <relate><relatenm>인터파크</relatenm><relateurl>http://a.example/1</relateurl></relate>
                <relate><relatenm>링크없음</relatenm><relateurl> </relateurl></relate>
                <relate><relatenm>멜론티켓</relatenm><relateurl>http://b.example/2</relateurl></relate>
                </relates></db></dbs>""");

        KopisClient.KopisDetail detail = client.getPerformanceDetail("PF1");

        assertThat(detail.ticketVendors()).containsExactly(
                new TicketVendorInfo("인터파크", "http://a.example/1"),
                new TicketVendorInfo("멜론티켓", "http://b.example/2"));
        assertThat(detail.ticketUrl()).isEqualTo("http://a.example/1");
    }

    @Test
    void 예매처가_없는_정상_응답은_null이_아니라_빈_목록이다() {
        givenResponse("<dbs><db><mt10id>FC1</mt10id><pcseguidance>전석 99,000원</pcseguidance></db></dbs>");

        KopisClient.KopisDetail detail = client.getPerformanceDetail("PF1");

        assertThat(detail.ticketVendors()).isEmpty();
        assertThat(detail.ticketUrl()).isNull();
        assertThat(detail.price()).isEqualTo("전석 99,000원");
    }

    @Test
    void 상세조회에_실패하면_모든_필드가_null이다() {
        givenFailure();

        KopisClient.KopisDetail detail = client.getPerformanceDetail("PF287093");

        assertThat(detail.price()).isNull();
        assertThat(detail.showtime()).isNull();
        assertThat(detail.ticketVendors()).isNull();
        assertThat(detail.ticketUrl()).isNull();
        assertThat(detail.facilityId()).isNull();
        assertThat(detail.hallId()).isNull();
    }

    // ---------- 공연장(시설) ----------

    @Test
    void 시설조회는_시설_경로에_시설ID로_요청한다() {
        givenResponse(FACILITY_XML);

        client.getFacility("FC003670", "FC003670-01");

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uri.capture(), eq(String.class));
        assertThat(uri.getValue().getPath()).isEqualTo("/openApi/restful/prfplc/FC003670");
    }

    @Test
    void 시설조회는_주소와_좌표를_읽고_수용인원은_공연이_열리는_홀의_값이다() {
        givenResponse(FACILITY_XML);

        KopisClient.KopisFacility facility = client.getFacility("FC003670", "FC003670-01");

        assertThat(facility.address()).isEqualTo("인천광역시 중구 공항문화로 127 (운서동)");
        assertThat(facility.latitude()).isEqualTo(37.4655301);
        assertThat(facility.longitude()).isEqualTo(126.3891177);
        // 시설 전체(18,483)가 아니라 아레나 홀(14,483). 천 단위 쉼표가 섞여 있어도 읽는다.
        assertThat(facility.capacity()).isEqualTo(14483);
    }

    @Test
    void 같은_시설의_다른_홀이면_그_홀의_수용인원이다() {
        givenResponse(FACILITY_XML);

        assertThat(client.getFacility("FC003670", "FC003670-03").capacity()).isEqualTo(3000);
    }

    @Test
    void 홀_정보가_시설에_없으면_시설_전체_수용인원으로_대신한다() {
        givenResponse(FACILITY_XML);

        assertThat(client.getFacility("FC003670", "FC003670-99").capacity()).isEqualTo(18483);
    }

    @Test
    void 공연에_홀ID가_없으면_시설_전체_수용인원으로_대신한다() {
        givenResponse(FACILITY_XML);

        assertThat(client.getFacility("FC003670", null).capacity()).isEqualTo(18483);
    }

    @Test
    void 홀은_찾았는데_수용인원이_0이면_시설_전체가_아니라_null이다() {
        givenResponse(FACILITY_XML);

        // 디스커버리 파크는 0석으로 등록돼 있다 - 이걸 시설 전체 18,483석으로 보여주면 오해다
        assertThat(client.getFacility("FC003670", "FC003670-02").capacity()).isNull();
    }

    @Test
    void 시설조회에_실패하면_모든_필드가_null이다() {
        givenFailure();

        KopisClient.KopisFacility facility = client.getFacility("FC003670", "FC003670-01");

        assertThat(facility.address()).isNull();
        assertThat(facility.latitude()).isNull();
        assertThat(facility.longitude()).isNull();
        assertThat(facility.capacity()).isNull();
    }

    @Test
    void 시설ID가_없으면_KOPIS를_호출하지_않고_모든_필드가_null이다() {
        KopisClient.KopisFacility facility = client.getFacility(null, "FC003670-01");

        verifyNoInteractions(restTemplate);
        assertThat(facility.address()).isNull();
        assertThat(facility.capacity()).isNull();
    }

    // ---------- 공연 목록 수집 ----------

    private static String listXml(int count) {
        StringBuilder xml = new StringBuilder("<dbs>");
        for (int i = 0; i < count; i++) {
            xml.append("<db><mt20id>PF").append(i).append("</mt20id><prfnm>공연 ").append(i).append("</prfnm>")
                    .append("<genrenm>대중음악</genrenm><prfpdfrom>2026.10.01</prfpdfrom><prfpdto>2026.10.02</prfpdto>")
                    .append("<fcltynm>공연장</fcltynm></db>");
        }
        return xml.append("</dbs>").toString();
    }

    @Test
    void 목록은_대중음악_장르코드로_한_페이지_100건씩_요청한다() {
        givenResponse(listXml(3));

        client.getAllUpcomingPerformances();

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uri.capture(), eq(String.class));
        assertThat(uri.getValue().getQuery()).contains("shcate=CCCD", "rows=100", "cpage=1");
    }

    @Test
    void 한_페이지가_100건_미만이면_거기서_끝내고_받은_공연을_돌려준다() {
        givenResponse(listXml(3));

        assertThat(client.getAllUpcomingPerformances()).hasSize(3);

        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void 한_페이지가_꽉_차면_다음_페이지를_이어서_요청한다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(listXml(100), listXml(40));

        assertThat(client.getAllUpcomingPerformances()).hasSize(140);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, times(2)).getForObject(uri.capture(), eq(String.class));
        assertThat(uri.getAllValues().get(0).getQuery()).contains("cpage=1");
        assertThat(uri.getAllValues().get(1).getQuery()).contains("cpage=2");
    }

    @Test
    void 중간_페이지가_실패하면_그때까지_받은_공연만_돌려준다() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(listXml(100))
                .thenThrow(new RestClientException("Request Blocked"));

        assertThat(client.getAllUpcomingPerformances()).hasSize(100);
    }

    // ---------- 요청 간격 (KOPIS 차단 방지) ----------
    // 시간 비교는 sleep 오차를 감안해 여유를 둔다. 간격이 없으면 이 시간들은 거의 0ms에 가깝다.

    private long elapsedMs(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    @Test
    void 첫_요청은_기다리지_않는다() {
        ReflectionTestUtils.setField(client, "requestDelayMs", 5000L);
        givenResponse(DETAIL_XML);

        long elapsed = elapsedMs(() -> client.getPerformanceDetail("PF287093"));

        assertThat(elapsed).isLessThan(2000);
    }

    @Test
    void 연속으로_요청하면_설정한_간격만큼_띄운다() {
        ReflectionTestUtils.setField(client, "requestDelayMs", 200L);
        givenResponse(DETAIL_XML);

        long elapsed = elapsedMs(() -> {
            client.getPerformanceDetail("PF1");
            client.getPerformanceDetail("PF2");
        });

        assertThat(elapsed).isGreaterThanOrEqualTo(150);
    }

    @Test
    void 목록_페이지를_넘길_때도_간격을_둔다() {
        ReflectionTestUtils.setField(client, "requestDelayMs", 200L);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(listXml(100), listXml(10));

        long elapsed = elapsedMs(() -> client.getAllUpcomingPerformances());

        assertThat(elapsed).isGreaterThanOrEqualTo(150);
    }

    @Test
    void 목록과_상세_시설_요청이_같은_간격을_공유한다() {
        ReflectionTestUtils.setField(client, "requestDelayMs", 200L);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(listXml(3), DETAIL_XML, FACILITY_XML);

        long elapsed = elapsedMs(() -> {
            client.getAllUpcomingPerformances();
            client.getPerformanceDetail("PF287093");
            client.getFacility("FC003670", "FC003670-01");
        });

        // 요청 3번 = 간격 2번
        assertThat(elapsed).isGreaterThanOrEqualTo(350);
    }

    // ---------- 차단 응답 재시도 ----------
    // KOPIS는 짧은 시간에 요청이 몰리면 400 + "Request Blocked" HTML로 차단하지만, 잠깐 뒤엔 풀리는 경우가 많다.

    /** 실제 로그와 같은 형태의 차단 응답(400 + Request Blocked 본문). */
    private static HttpClientErrorException blocked() {
        return HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "<HTML><H1>Request Blocked</H1></HTML>".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    /** 차단이 아닌 진짜 400(예: 잘못된 파라미터). */
    private static HttpClientErrorException badRequest() {
        return HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "<error>invalid parameter</error>".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private void givenRetry(int max, long waitMs) {
        ReflectionTestUtils.setField(client, "blockedRetryMax", max);
        ReflectionTestUtils.setField(client, "blockedRetryWaitMs", waitMs);
    }

    @Test
    void 상세조회가_한_번_차단돼도_기다렸다_재시도하면_정상_결과를_받는다() {
        givenRetry(3, 1);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenThrow(blocked())
                .thenReturn(DETAIL_XML);

        KopisClient.KopisDetail detail = client.getPerformanceDetail("PF287093");

        assertThat(detail.price()).isEqualTo("스탠딩석 165,000원, R석 165,000원, S석 154,000원");
        verify(restTemplate, times(2)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void 목록_페이지가_차단돼도_재시도로_이어서_끝까지_수집한다() {
        givenRetry(3, 1);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(listXml(100))   // 1페이지 정상
                .thenThrow(blocked())        // 2페이지 첫 시도는 차단
                .thenReturn(listXml(10));    // 2페이지 재시도 성공

        // 재시도가 없으면 100건에서 끊기는 상황이다
        assertThat(client.getAllUpcomingPerformances()).hasSize(110);
        verify(restTemplate, times(3)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void 재시도_한도까지_계속_차단되면_포기하고_실패로_처리한다() {
        givenRetry(2, 1);
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenThrow(blocked());

        KopisClient.KopisDetail detail = client.getPerformanceDetail("PF287093");

        assertThat(detail.price()).isNull();
        // 첫 요청 1번 + 재시도 2번
        verify(restTemplate, times(3)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void 차단이_아닌_400은_재시도하지_않는다() {
        givenRetry(3, 1);
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenThrow(badRequest());

        client.getPerformanceDetail("PF287093");

        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void 재시도_횟수를_0으로_두면_차단돼도_재시도하지_않는다() {
        givenRetry(0, 1);
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenThrow(blocked());

        client.getPerformanceDetail("PF287093");

        verify(restTemplate, times(1)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    void 재시도_전에_설정한_시간만큼_기다린다() {
        givenRetry(1, 200);
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenThrow(blocked())
                .thenReturn(DETAIL_XML);

        long elapsed = elapsedMs(() -> client.getPerformanceDetail("PF287093"));

        assertThat(elapsed).isGreaterThanOrEqualTo(150);
    }
}