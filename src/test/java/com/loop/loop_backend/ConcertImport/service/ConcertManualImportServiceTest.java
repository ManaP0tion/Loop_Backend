package com.loop.loop_backend.ConcertImport.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.domain.Nationality;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.ConcertImport.domain.ConcertImport;
import com.loop.loop_backend.ConcertImport.domain.ImportStatus;
import com.loop.loop_backend.ConcertImport.dto.KopisSearchItem;
import com.loop.loop_backend.ConcertImport.dto.KopisSearchResponse;
import com.loop.loop_backend.ConcertImport.dto.KopisSearchStatus;
import com.loop.loop_backend.ConcertImport.dto.ManualImportRequest;
import com.loop.loop_backend.ConcertImport.kopis.KopisClient;
import com.loop.loop_backend.ConcertImport.kopis.KopisPerformance;
import com.loop.loop_backend.ConcertImport.kopis.PerformanceIdentifier;
import com.loop.loop_backend.ConcertImport.repository.ConcertImportRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// KOPIS 직접 등록 요구사항:
// [검색]
// - 공연명 검색어가 비어 있으면 거절한다.
// - 검색 기간은 오늘 기준 2년 전 ~ 1년 후, 최대 200건.
// - 결과마다 등록 상태: 공연이 있으면 REGISTERED(공연 id 포함) > 대기열 대기 PENDING > 대기열 거절 REJECTED > 그 외 NEW.
//   승인됐지만 공연이 삭제된 건은 NEW다.
// - 시작일 최신순, 시작일 없는 공연은 맨 뒤.
// - 결과마다 추천 아티스트: 공연명에 이름이 들어 있는 J-POP 아티스트 전부(자동 수집 여부 무관). 없으면 빈 목록.
// [등록]
// - 유형은 관리자가 고를 수 있는 유형(내한 공연, 페스티벌)만. 페스티벌은 아티스트 없이, 내한 공연은 0~1명.
// - 이 KOPIS 공연으로 만든 공연이 이미 있으면(아티스트와 상관없이) 409.
// - 잘못된 요청과 중복 등록에는 KOPIS를 부르지 않는다.
// - KOPIS 상세는 1번만 조회하고, 그 값으로 대기열 행을 채워 바로 승인한다.
// - 같은 (공연, 아티스트) 대기열 행이 있으면 대기·거절 상관없이 그 행을 다시 쓴다(행이 둘 생기지 않는다).
@ExtendWith(MockitoExtension.class)
class ConcertManualImportServiceTest {

    @Mock KopisClient kopisClient;
    @Mock ConcertRepository concertRepository;
    @Mock ConcertImportRepository importRepository;
    @Mock ArtistRepository artistRepository;
    @Mock ConcertImportService concertImportService;
    // 추천 아티스트는 대기열 자동 매칭과 같은 공연명 규칙이어야 하므로 실제 규칙을 쓴다
    @Spy PerformanceIdentifier identifier = new PerformanceIdentifier();
    @InjectMocks ConcertManualImportService service;

    private static final String KOPIS_ID = "PF287093";
    private static final Artist VAUNDY = Artist.builder().id(3L).name("Vaundy").build();

    private static final KopisPerformance KOPIS_PERF = KopisPerformance.builder()
            .kopisId(KOPIS_ID)
            .title("Vaundy ASIA ARENA TOUR HORO IN SEOUL")
            .posterUrl("https://www.kopis.or.kr/poster.gif")
            .venue("인스파이어 엔터테인먼트 리조트 (아레나)")
            .startDate(LocalDate.of(2026, 9, 19))
            .endDate(LocalDate.of(2026, 9, 20))
            .build();
    private static final KopisClient.KopisDetail KOPIS_DETAIL = new KopisClient.KopisDetail(
            "스탠딩석 165,000원", "토요일(17:00), 일요일(16:00)", List.of(), "FC003670", "FC003670-01");

    private static ManualImportRequest request(ConcertCategory category, List<Long> artistIds) {
        return new ManualImportRequest(KOPIS_ID, category, artistIds);
    }

    private void givenKopis() {
        when(kopisClient.getPerformanceForImport(KOPIS_ID))
                .thenReturn(new KopisClient.ImportSource(KOPIS_PERF, KOPIS_DETAIL));
    }

    private void givenPublishSucceeds() {
        when(concertImportService.publish(any(), any())).thenReturn(Concert.builder().id(100L).build());
    }

    private ConcertImport publishedImport() {
        ArgumentCaptor<ConcertImport> captor = ArgumentCaptor.forClass(ConcertImport.class);
        verify(concertImportService).publish(captor.capture(), eq(KOPIS_DETAIL));
        return captor.getValue();
    }

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    // ---------- 등록: 요청 검증 ----------

    @Test
    @DisplayName("페스티벌에 아티스트를 지정하면 거절하고 KOPIS를 부르지 않는다")
    void register_FestivalWithArtist_RejectsWithoutCallingKopis() {
        // when & then
        assertErrorCode(() -> service.register(request(ConcertCategory.FESTIVAL, List.of(3L))),
                ErrorCode.INVALID_INPUT);
        verifyNoInteractions(kopisClient);
    }

    @Test
    @DisplayName("내한 공연에 아티스트를 2명 이상 지정하면 거절한다")
    void register_SoloWithTwoArtists_Rejects() {
        // when & then
        assertErrorCode(() -> service.register(request(ConcertCategory.STAGE, List.of(3L, 4L))),
                ErrorCode.INVALID_INPUT);
        verifyNoInteractions(kopisClient);
    }

    @Test
    @DisplayName("공연 유형 없이는 등록할 수 없다")
    void register_WithoutCategory_Rejects() {
        // when & then
        assertErrorCode(() -> service.register(request(null, List.of())),
                ErrorCode.INVALID_INPUT);
        verifyNoInteractions(kopisClient);
    }

    @Test
    @DisplayName("없는 아티스트를 지정하면 아티스트 없음으로 거절한다")
    void register_UnknownArtist_ThrowsArtistNotFound() {
        // given
        when(artistRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertErrorCode(() -> service.register(request(ConcertCategory.STAGE, List.of(99L))),
                ErrorCode.ARTIST_NOT_FOUND);
        verifyNoInteractions(kopisClient);
    }

    @Test
    @DisplayName("이 KOPIS 공연으로 만든 공연이 이미 있으면 409로 거절하고 KOPIS를 부르지 않는다")
    void register_AlreadyRegistered_ThrowsConflictWithoutCallingKopis() {
        // given - 다른 아티스트로 등록된 공연이라도 같은 KOPIS 공연이면 막는다
        when(concertRepository.existsByKopisId(KOPIS_ID)).thenReturn(true);

        // when & then
        assertErrorCode(() -> service.register(request(ConcertCategory.STAGE, List.of())),
                ErrorCode.CONCERT_ALREADY_REGISTERED);
        verifyNoInteractions(kopisClient);
        verify(concertImportService, never()).publish(any(), any());
    }

    // ---------- 등록: 정상 흐름 ----------

    @Test
    @DisplayName("처음 보는 공연은 KOPIS 값으로 직접 등록 대기열 행을 만들어 바로 승인한다")
    void register_NewPerformance_CreatesManualImportAndPublishes() {
        // given
        when(artistRepository.findById(3L)).thenReturn(Optional.of(VAUNDY));
        when(importRepository.findByKopisIdAndMatchedArtist_Id(KOPIS_ID, 3L)).thenReturn(Optional.empty());
        givenKopis();
        givenPublishSucceeds();

        // when
        service.register(request(ConcertCategory.STAGE, List.of(3L)));

        // then
        ConcertImport imp = publishedImport();
        assertThat(imp.getKopisId()).isEqualTo(KOPIS_ID);
        assertThat(imp.getMatchedArtist()).isEqualTo(VAUNDY);
        assertThat(imp.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR HORO IN SEOUL");
        assertThat(imp.getPosterUrl()).isEqualTo("https://www.kopis.or.kr/poster.gif");
        assertThat(imp.getVenue()).isEqualTo("인스파이어 엔터테인먼트 리조트 (아레나)");
        assertThat(imp.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 19));
        assertThat(imp.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(imp.getSuggestedCategory()).isEqualTo(ConcertCategory.STAGE);
        assertThat(imp.getMatchReason()).isEqualTo(ConcertImport.MATCH_REASON_MANUAL);
        verify(importRepository).save(imp);
    }

    @Test
    @DisplayName("KOPIS 상세는 1번만 조회하고 승인할 때 다시 조회하지 않는다")
    void register_FetchesKopisDetailOnlyOnce() {
        // given
        when(importRepository.findByKopisIdAndMatchedArtistIsNull(KOPIS_ID)).thenReturn(Optional.empty());
        givenKopis();
        givenPublishSucceeds();

        // when
        service.register(request(ConcertCategory.STAGE, List.of()));

        // then - 승인에는 받은 상세를 그대로 넘긴다
        verify(kopisClient).getPerformanceForImport(KOPIS_ID);
        verify(kopisClient, never()).getPerformanceDetail(anyString());
        publishedImport();
    }

    @Test
    @DisplayName("아티스트 없이 내한 공연을 등록할 수 있다")
    void register_SoloWithoutArtist_CreatesImportWithoutArtist() {
        // given
        when(importRepository.findByKopisIdAndMatchedArtistIsNull(KOPIS_ID)).thenReturn(Optional.empty());
        givenKopis();
        givenPublishSucceeds();

        // when
        service.register(request(ConcertCategory.STAGE, null));

        // then
        assertThat(publishedImport().getMatchedArtist()).isNull();
    }

    @Test
    @DisplayName("페스티벌은 아티스트 없이 페스티벌 유형으로 등록된다")
    void register_Festival_CreatesFestivalImportWithoutArtist() {
        // given
        when(importRepository.findByKopisIdAndMatchedArtistIsNull(KOPIS_ID)).thenReturn(Optional.empty());
        givenKopis();
        givenPublishSucceeds();

        // when
        service.register(request(ConcertCategory.FESTIVAL, List.of()));

        // then
        ConcertImport imp = publishedImport();
        assertThat(imp.getMatchedArtist()).isNull();
        assertThat(imp.getSuggestedCategory()).isEqualTo(ConcertCategory.FESTIVAL);
    }

    @Test
    @DisplayName("거절된 대기열 행이 있으면 새로 만들지 않고 그 행을 다시 열어 승인한다")
    void register_RejectedImport_ReopensSameRowAndPublishes() {
        // given
        ConcertImport rejected = ConcertImport.builder()
                .id(7L).kopisId(KOPIS_ID).matchedArtist(VAUNDY).title("옛 공연명")
                .suggestedCategory(ConcertCategory.STAGE).matchReason("TITLE_MATCH")
                .status(ImportStatus.PENDING).build();
        rejected.markRejected("일본 아티스트 아님");
        when(artistRepository.findById(3L)).thenReturn(Optional.of(VAUNDY));
        when(importRepository.findByKopisIdAndMatchedArtist_Id(KOPIS_ID, 3L)).thenReturn(Optional.of(rejected));
        givenKopis();
        givenPublishSucceeds();

        // when
        var response = service.register(request(ConcertCategory.STAGE, List.of(3L)));

        // then
        ConcertImport imp = publishedImport();
        assertThat(imp).isSameAs(rejected);
        assertThat(imp.getStatus()).isEqualTo(ImportStatus.PENDING);
        assertThat(imp.getRejectReason()).isNull();
        assertThat(imp.getTitle()).isEqualTo("Vaundy ASIA ARENA TOUR HORO IN SEOUL");
        assertThat(imp.getMatchReason()).isEqualTo(ConcertImport.MATCH_REASON_MANUAL);
        assertThat(response.importId()).isEqualTo(7L);
        assertThat(response.concertId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("검토 대기 중인 행이 있으면 그 행을 관리자가 고른 유형으로 바꿔 승인한다")
    void register_PendingImport_ReusesRowWithChosenCategory() {
        // given - 자동 수집이 내한 공연으로 넣어 둔 행을 관리자가 페스티벌로 등록
        ConcertImport pending = ConcertImport.builder()
                .id(8L).kopisId(KOPIS_ID).title("옛 공연명")
                .suggestedCategory(ConcertCategory.STAGE).matchReason("TITLE_MATCH")
                .status(ImportStatus.PENDING).build();
        when(importRepository.findByKopisIdAndMatchedArtistIsNull(KOPIS_ID)).thenReturn(Optional.of(pending));
        givenKopis();
        givenPublishSucceeds();

        // when
        service.register(request(ConcertCategory.FESTIVAL, List.of()));

        // then
        ConcertImport imp = publishedImport();
        assertThat(imp).isSameAs(pending);
        assertThat(imp.getSuggestedCategory()).isEqualTo(ConcertCategory.FESTIVAL);
    }

    // ---------- 검색 ----------

    private static KopisPerformance perf(String kopisId, LocalDate startDate) {
        return KopisPerformance.builder().kopisId(kopisId).title("공연 " + kopisId).startDate(startDate).build();
    }

    private static ConcertImport importRow(String kopisId, ImportStatus status) {
        return ConcertImport.builder().kopisId(kopisId).title("공연 " + kopisId)
                .suggestedCategory(ConcertCategory.STAGE).status(status).build();
    }

    @Test
    @DisplayName("검색어가 비어 있으면 거절하고 KOPIS를 부르지 않는다")
    void search_BlankKeyword_RejectsWithoutCallingKopis() {
        // when & then
        assertErrorCode(() -> service.search("  "), ErrorCode.INVALID_INPUT);
        verifyNoInteractions(kopisClient);
    }

    @Test
    @DisplayName("검색은 오늘 기준 2년 전부터 1년 후까지 최대 200건을 찾는다")
    void search_UsesTwoYearsBackToOneYearAheadUpTo200() {
        // given
        LocalDate today = LocalDate.now();
        when(kopisClient.searchPerformances(anyString(), any(), any(), any(Integer.class)))
                .thenReturn(new KopisClient.SearchResult(List.of(), false));

        // when
        service.search(" Vaundy ");

        // then
        verify(kopisClient).searchPerformances("Vaundy", today.minusYears(2), today.plusYears(1), 200);
    }

    @Test
    @DisplayName("검색 결과마다 공연·검토 대기열 기록으로 등록 상태를 붙인다")
    void search_AttachesRegistrationStatus() {
        // given
        LocalDate date = LocalDate.of(2026, 9, 19);
        when(kopisClient.searchPerformances(anyString(), any(), any(), any(Integer.class)))
                .thenReturn(new KopisClient.SearchResult(List.of(
                        perf("PF_REG", date), perf("PF_PENDING", date), perf("PF_REJECTED", date),
                        perf("PF_NEW", date), perf("PF_DELETED", date)), false));
        // 등록됨: 공연 2건(아티스트별) + 승인된 대기열 행
        when(concertRepository.findByKopisIdIn(any())).thenReturn(List.of(
                Concert.builder().id(10L).kopisId("PF_REG").build(),
                Concert.builder().id(11L).kopisId("PF_REG").build()));
        when(importRepository.findByKopisIdIn(any())).thenReturn(List.of(
                importRow("PF_REG", ImportStatus.APPROVED),
                importRow("PF_PENDING", ImportStatus.PENDING),
                importRow("PF_PENDING", ImportStatus.REJECTED),
                importRow("PF_REJECTED", ImportStatus.REJECTED),
                // 승인했지만 공연이 삭제된 건
                importRow("PF_DELETED", ImportStatus.APPROVED)));

        // when
        KopisSearchResponse response = service.search("공연");

        // then
        assertThat(response.items())
                .extracting(KopisSearchItem::kopisId, KopisSearchItem::status, KopisSearchItem::concertIds)
                .containsExactlyInAnyOrder(
                        tuple("PF_REG", KopisSearchStatus.REGISTERED, List.of(10L, 11L)),
                        tuple("PF_PENDING", KopisSearchStatus.PENDING, List.of()),
                        tuple("PF_REJECTED", KopisSearchStatus.REJECTED, List.of()),
                        tuple("PF_NEW", KopisSearchStatus.NEW, List.of()),
                        tuple("PF_DELETED", KopisSearchStatus.NEW, List.of()));
    }

    @Test
    @DisplayName("검색 결과는 시작일 최신순이고 시작일이 없는 공연은 맨 뒤에 온다")
    void search_SortsByStartDateDescWithUndatedLast() {
        // given
        when(kopisClient.searchPerformances(anyString(), any(), any(), any(Integer.class)))
                .thenReturn(new KopisClient.SearchResult(List.of(
                        perf("PF_OLD", LocalDate.of(2025, 1, 1)),
                        perf("PF_NODATE", null),
                        perf("PF_NEW", LocalDate.of(2026, 9, 19))), true));

        // when
        KopisSearchResponse response = service.search("공연");

        // then
        assertThat(response.items()).extracting(KopisSearchItem::kopisId)
                .containsExactly("PF_NEW", "PF_OLD", "PF_NODATE");
        assertThat(response.truncated()).isTrue();
    }

    private static KopisPerformance titled(String kopisId, String title) {
        return KopisPerformance.builder().kopisId(kopisId).title(title).build();
    }

    private static List<KopisSearchItem.SuggestedArtist> suggestedOf(KopisSearchResponse response, String kopisId) {
        return response.items().stream().filter(i -> i.kopisId().equals(kopisId)).findFirst()
                .orElseThrow().suggestedArtists();
    }

    @Test
    @DisplayName("공연명에 국내가 아닌 아티스트 이름이 있으면 자동 수집이 꺼졌거나 국적 미입력이라도 추천하고, 여러 명이면 모두 추천한다")
    void search_SuggestsNonDomesticArtistsNamedInTitle() {
        // given - 둘 다 자동 수집이 꺼진 아티스트, Ado는 국적 미입력
        Artist yoasobi = Artist.builder().id(5L).name("YOASOBI").nameKo("요아소비")
                .nationality(Nationality.JP).autoFetchConcerts(false).build();
        Artist ado = Artist.builder().id(6L).name("Ado").autoFetchConcerts(false).build();
        when(artistRepository.findByNationalityNotOrNationalityIsNull(Nationality.KR)).thenReturn(List.of(yoasobi, ado));
        when(kopisClient.searchPerformances(anyString(), any(), any(), any(Integer.class)))
                .thenReturn(new KopisClient.SearchResult(List.of(
                        titled("PF_SOLO", "요아소비 내한 공연"),
                        titled("PF_BOTH", "YOASOBI × Ado SPECIAL LIVE")), false));

        // when
        KopisSearchResponse response = service.search("공연");

        // then
        assertThat(suggestedOf(response, "PF_SOLO"))
                .containsExactly(new KopisSearchItem.SuggestedArtist(5L, "YOASOBI"));
        assertThat(suggestedOf(response, "PF_BOTH"))
                .containsExactlyInAnyOrder(new KopisSearchItem.SuggestedArtist(5L, "YOASOBI"),
                        new KopisSearchItem.SuggestedArtist(6L, "Ado"));
    }

    @Test
    @DisplayName("공연명에 아는 아티스트 이름이 없으면 추천 아티스트는 빈 목록이다")
    void search_NoArtistNamedInTitle_SuggestsNone() {
        // given
        when(artistRepository.findByNationalityNotOrNationalityIsNull(Nationality.KR)).thenReturn(List.of(VAUNDY));
        when(kopisClient.searchPerformances(anyString(), any(), any(), any(Integer.class)))
                .thenReturn(new KopisClient.SearchResult(List.of(titled("PF1", "ASIA ARENA TOUR IN SEOUL")), false));

        // when
        KopisSearchResponse response = service.search("TOUR");

        // then
        assertThat(response.items().get(0).suggestedArtists()).isEmpty();
    }
}
