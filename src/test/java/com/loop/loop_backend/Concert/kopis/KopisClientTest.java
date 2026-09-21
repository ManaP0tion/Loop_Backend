package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
}