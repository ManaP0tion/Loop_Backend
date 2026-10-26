package com.loop.loop_backend.ConcertImport.kopis;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 관리자 직접 등록용 KOPIS 조회 요구사항:
// - 공연명 검색은 장르 제한 없이 요청한 기간·검색어로 찾고, 최대 건수까지만 받는다. 최대 건수에 걸리면 잘렸을 수 있다고 알린다.
// - 검색 중 KOPIS가 실패하면 일부 결과를 보여주지 않고 실패(502)로 알린다.
// - 등록용 조회는 상세 1번으로 기본 정보(공연명·포스터·공연장명·기간)와 상세(가격·공연 시간·예매처·시설·홀)를 함께 준다.
// - 등록용 조회에서 KOPIS가 실패하면 502, KOPIS에 없는 공연이면 404.
class KopisClientManualImportTest {

    // 실제 공연 상세 응답 (Vaundy ASIA ARENA TOUR HORO IN SEOUL)
    private static final String DETAIL_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?><dbs><db><mt20id>PF287093</mt20id><mt13id>FC003670-01</mt13id> <prfnm>Vaundy ASIA ARENA TOUR HORO IN SEOUL</prfnm><prfpdfrom>2026.09.19</prfpdfrom><prfpdto>2026.09.20</prfpdto> <fcltynm>인스파이어 엔터테인먼트 리조트 (아레나)</fcltynm> <prfcast> </prfcast> <prfruntime>2시간</prfruntime> <prfage>만 7세 이상</prfage> <pcseguidance>스탠딩석 165,000원, R석 165,000원, S석 154,000원</pcseguidance> <poster>http://www.kopis.or.kr/upload/pfmPoster/PF_PF287093_260313_172418.gif </poster> <sty> </sty> <area>인천광역시</area> <genrenm>대중음악</genrenm> <prfstate>공연예정</prfstate><mt10id>FC003670</mt10id> <dtguidance>토요일(17:00), 일요일(16:00)</dtguidance><relates>  <relate>   <relatenm>놀유니버스</relatenm>   <relateurl>http://ticket.interpark.com/Ticket/Goods/GoodsInfo.asp?GoodsCode=26003199 </relateurl> </relate></relates></db></dbs>""";

    private static final LocalDate FROM = LocalDate.of(2024, 10, 26);
    private static final LocalDate TO = LocalDate.of(2027, 10, 26);

    private RestTemplate restTemplate;
    private KopisClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new KopisClient(restTemplate);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
    }

    private static String listItem(String kopisId, String title, String genre) {
        return "<db><mt20id>" + kopisId + "</mt20id><prfnm>" + title + "</prfnm>"
                + "<prfpdfrom>2026.09.19</prfpdfrom><prfpdto>2026.09.20</prfpdto>"
                + "<fcltynm>공연장</fcltynm><poster>http://www.kopis.or.kr/p.gif</poster>"
                + "<genrenm>" + genre + "</genrenm></db>";
    }

    /** n건짜리 목록 페이지. 공연 ID는 prefix + 번호 */
    private static String listPage(String prefix, int n) {
        return "<dbs>" + IntStream.range(0, n)
                .mapToObj(i -> listItem(prefix + i, "공연 " + prefix + i, "대중음악"))
                .collect(Collectors.joining()) + "</dbs>";
    }

    private URI capturedSingleUri() {
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uri.capture(), eq(String.class));
        return uri.getValue();
    }

    private static String queryParam(URI uri, String name) {
        return UriComponentsBuilder.fromUri(uri).build(true).getQueryParams().getFirst(name);
    }

    // ---------- 공연명 검색 ----------

    @Test
    @DisplayName("공연명 검색은 장르를 거르지 않아 대중음악이 아닌 공연도 결과에 나온다")
    void searchPerformances_WithNonPopularGenre_IncludesAll() {
        // given
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn("<dbs>"
                + listItem("PF1", "SUMMER SONIC 2026", "대중음악")
                + listItem("PF2", "어느 페스티벌", "복합")
                + "</dbs>");

        // when
        KopisClient.SearchResult result = client.searchPerformances("페스티벌", FROM, TO, 200);

        // then
        assertThat(result.performances()).extracting(KopisPerformance::getKopisId).containsExactly("PF1", "PF2");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("공연명 검색은 검색어와 요청한 기간으로 KOPIS에 묻고, 장르 조건은 보내지 않는다")
    void searchPerformances_SendsKeywordAndPeriodWithoutGenre() {
        // given
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn("<dbs></dbs>");

        // when
        client.searchPerformances("Vaundy", FROM, TO, 200);

        // then
        URI uri = capturedSingleUri();
        assertThat(queryParam(uri, "shprfnm")).isEqualTo("Vaundy");
        assertThat(queryParam(uri, "stdate")).isEqualTo("20241026");
        assertThat(queryParam(uri, "eddate")).isEqualTo("20271026");
        assertThat(queryParam(uri, "shcate")).isNull();
    }

    @Test
    @DisplayName("검색 결과가 최대 건수보다 적으면 전부 받고 잘리지 않았다고 알린다")
    void searchPerformances_FewerThanMax_ReturnsAllNotTruncated() {
        // given - 1페이지 100건(가득), 2페이지 30건
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(listPage("A", 100))
                .thenReturn(listPage("B", 30));

        // when
        KopisClient.SearchResult result = client.searchPerformances("공연", FROM, TO, 200);

        // then
        assertThat(result.performances()).hasSize(130);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    @DisplayName("검색 결과가 최대 건수에 걸리면 더 요청하지 않고 잘렸을 수 있다고 알린다")
    void searchPerformances_ReachesMax_StopsAndMarksTruncated() {
        // given - 매 페이지 100건씩 가득
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(listPage("A", 100))
                .thenReturn(listPage("B", 100))
                .thenReturn(listPage("C", 100));

        // when
        KopisClient.SearchResult result = client.searchPerformances("공연", FROM, TO, 200);

        // then
        assertThat(result.performances()).hasSize(200);
        assertThat(result.truncated()).isTrue();
        verify(restTemplate, times(2)).getForObject(any(URI.class), eq(String.class));
    }

    @Test
    @DisplayName("검색 중 KOPIS 조회가 실패하면 받은 만큼만 주지 않고 KOPIS 조회 실패로 알린다")
    void searchPerformances_WhenKopisFails_ThrowsUnavailable() {
        // given - 1페이지는 받고 2페이지에서 실패
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn(listPage("A", 100))
                .thenThrow(new RestClientException("boom"));

        // when & then
        assertThatThrownBy(() -> client.searchPerformances("공연", FROM, TO, 200))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KOPIS_UNAVAILABLE);
    }

    // ---------- 직접 등록용 상세 ----------

    @Test
    @DisplayName("등록용 조회는 상세 1번으로 기본 정보와 승인에 쓸 상세를 함께 준다")
    void getPerformanceForImport_ReturnsBasicInfoAndDetailFromOneRequest() {
        // given
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(DETAIL_XML);

        // when
        KopisClient.ImportSource source = client.getPerformanceForImport("PF287093");

        // then - 기본 정보
        KopisPerformance perf = source.performance();
        assertThat(perf.getKopisId()).isEqualTo("PF287093");
        assertThat(perf.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR HORO IN SEOUL");
        assertThat(perf.getVenue()).isEqualTo("인스파이어 엔터테인먼트 리조트 (아레나)");
        assertThat(perf.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(perf.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(perf.getPosterUrl()).isEqualTo("https://www.kopis.or.kr/upload/pfmPoster/PF_PF287093_260313_172418.gif");
        // then - 상세
        KopisClient.KopisDetail detail = source.detail();
        assertThat(detail.price()).isEqualTo("스탠딩석 165,000원, R석 165,000원, S석 154,000원");
        assertThat(detail.showtime()).isEqualTo("토요일(17:00), 일요일(16:00)");
        assertThat(detail.facilityId()).isEqualTo("FC003670");
        assertThat(detail.hallId()).isEqualTo("FC003670-01");
        assertThat(detail.ticketVendors()).containsExactly(new TicketVendorInfo("놀유니버스",
                "http://ticket.interpark.com/Ticket/Goods/GoodsInfo.asp?GoodsCode=26003199"));
        // then - KOPIS 요청은 공연 상세 경로로 1번
        assertThat(capturedSingleUri().getPath()).isEqualTo("/openApi/restful/pblprfr/PF287093");
    }

    @Test
    @DisplayName("등록용 조회에서 KOPIS가 실패하면 KOPIS 조회 실패로 알린다")
    void getPerformanceForImport_WhenKopisFails_ThrowsUnavailable() {
        // given
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenThrow(new RestClientException("boom"));

        // when & then
        assertThatThrownBy(() -> client.getPerformanceForImport("PF287093"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KOPIS_UNAVAILABLE);
    }

    @Test
    @DisplayName("등록용 조회에서 KOPIS에 없는 공연이면 공연 없음으로 알린다")
    void getPerformanceForImport_WhenNotInKopis_ThrowsNotFound() {
        // given
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
                .thenReturn("<?xml version=\"1.0\" encoding=\"UTF-8\"?><dbs></dbs>");

        // when & then
        assertThatThrownBy(() -> client.getPerformanceForImport("PF000000"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.KOPIS_PERFORMANCE_NOT_FOUND);
    }
}
