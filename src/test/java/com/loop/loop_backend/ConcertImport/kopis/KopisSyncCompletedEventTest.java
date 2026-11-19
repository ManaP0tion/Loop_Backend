package com.loop.loop_backend.ConcertImport.kopis;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.domain.Nationality;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.ConcertImport.event.KopisSyncCompletedEvent;
import com.loop.loop_backend.ConcertImport.event.KopisSyncCompletedEvent.NewImport;
import com.loop.loop_backend.ConcertImport.repository.ConcertImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 싱크 완료 이벤트 요구사항:
// - 싱크가 끝나면 실행 경로(자동/수동)와 함께 완료 이벤트를 1번 발행한다.
// - 이벤트에는 이번 싱크로 검토 대기열에 새로 들어온 공연만 공연 단위로 담긴다(아티스트가 여럿이어도 1건).
// - 재수집으로 갱신되거나 그대로인 공연은 담지 않는다(이미 알린 공연).
// - 처리 중 실패한 공연 수를 담는다.
// KOPIS 클라이언트만 가짜로 두고 저장은 실제 DB로 한다 - "새로 들어왔는지"는 대기열에 실제로 새 행이 생겼는지로 정해진다.
@DataJpaTest
class KopisSyncCompletedEventTest {

    @Autowired private ArtistRepository artistRepository;
    @Autowired private ConcertImportRepository concertImportRepository;

    private KopisClient kopisClient;
    private ApplicationEventPublisher eventPublisher;
    private KopisSyncService service;

    @BeforeEach
    void setUp() {
        kopisClient = mock(KopisClient.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new KopisSyncService(artistRepository, kopisClient, new PerformanceIdentifier(),
                new ConcertImportWriter(concertImportRepository), eventPublisher);
    }

    private void jpopArtist(String name) {
        artistRepository.save(Artist.builder().name(name).autoFetchConcerts(true)
                .nationality(Nationality.JP).build());
    }

    private static KopisPerformance perf(String kopisId, String title) {
        return KopisPerformance.builder().kopisId(kopisId).title(title).venue("인스파이어 아레나")
                .startDate(LocalDate.of(2026, 12, 1)).endDate(LocalDate.of(2026, 12, 2)).build();
    }

    private KopisSyncCompletedEvent syncAndCaptureEvent(SyncTrigger trigger, KopisPerformance... performances) {
        when(kopisClient.getAllUpcomingPerformances()).thenReturn(List.of(performances));
        service.syncAll(trigger);
        ArgumentCaptor<KopisSyncCompletedEvent> captor = ArgumentCaptor.forClass(KopisSyncCompletedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("싱크가 끝나면 새로 들어온 공연과 실행 경로를 담아 완료 이벤트를 발행한다")
    void syncAll_PublishesEventWithNewImportsAndTrigger() {
        // given
        jpopArtist("YOASOBI");

        // when
        KopisSyncCompletedEvent event = syncAndCaptureEvent(SyncTrigger.SCHEDULED,
                perf("PF1", "YOASOBI ASIA TOUR 2026"),
                perf("PF2", "SUMMER SONIC 2026"),
                perf("PF3", "국내 가수 단독 콘서트"));

        // then - 매칭 안 된 공연은 대기열에 안 들어가므로 빠진다
        assertThat(event.trigger()).isEqualTo(SyncTrigger.SCHEDULED);
        assertThat(event.failed()).isZero();
        assertThat(event.newImports())
                .extracting(NewImport::title, NewImport::category, NewImport::artistNames)
                .containsExactlyInAnyOrder(
                        tuple("YOASOBI ASIA TOUR 2026", ConcertCategory.STAGE, List.of("YOASOBI")),
                        tuple("SUMMER SONIC 2026", ConcertCategory.FESTIVAL, List.of()));
    }

    @Test
    @DisplayName("한 공연에 아티스트가 여럿 매칭되면 공연 1건으로 담기고 아티스트 이름이 모두 들어간다")
    void syncAll_MultipleArtistsInOnePerformance_CountsAsOneNewImport() {
        // given
        jpopArtist("YOASOBI");
        jpopArtist("Ado");

        // when
        KopisSyncCompletedEvent event = syncAndCaptureEvent(SyncTrigger.SCHEDULED,
                perf("PF1", "YOASOBI × Ado SPECIAL LIVE"));

        // then
        assertThat(event.newImports()).singleElement()
                .satisfies(imp -> assertThat(imp.artistNames()).containsExactlyInAnyOrder("YOASOBI", "Ado"));
    }

    @Test
    @DisplayName("이미 대기열에 있는 공연을 다시 수집하면 새로 들어온 공연에 담지 않는다")
    void syncAll_AlreadyCollected_NotIncludedAsNew() {
        // given - 첫 싱크로 이미 들어온 공연
        jpopArtist("YOASOBI");
        when(kopisClient.getAllUpcomingPerformances()).thenReturn(List.of(perf("PF1", "YOASOBI ASIA TOUR 2026")));
        service.syncAll(SyncTrigger.SCHEDULED);

        // when - 같은 공연을 다시 수집(공연명이 바뀌어 갱신돼도 새 공연이 아니다)
        when(kopisClient.getAllUpcomingPerformances()).thenReturn(List.of(perf("PF1", "YOASOBI ASIA TOUR 2026 [서울]")));
        service.syncAll(SyncTrigger.SCHEDULED);

        // then
        ArgumentCaptor<KopisSyncCompletedEvent> captor = ArgumentCaptor.forClass(KopisSyncCompletedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues().get(0).newImports()).hasSize(1);
        assertThat(captor.getAllValues().get(1).newImports()).isEmpty();
    }

    @Test
    @DisplayName("어드민 수동 실행도 수동 실행 경로를 담아 완료 이벤트를 발행한다")
    void syncAll_Manual_PublishesEventWithManualTrigger() {
        // when
        KopisSyncCompletedEvent event = syncAndCaptureEvent(SyncTrigger.MANUAL);

        // then
        assertThat(event.trigger()).isEqualTo(SyncTrigger.MANUAL);
        assertThat(event.newImports()).isEmpty();
    }
}
