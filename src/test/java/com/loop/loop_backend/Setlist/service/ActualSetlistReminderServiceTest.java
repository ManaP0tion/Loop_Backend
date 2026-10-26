package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Notification.service.DiscordNotificationService;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import com.loop.loop_backend.Setlist.repository.SetlistRepository;
import com.loop.loop_backend.Song.Repository.SongRepository;
import com.loop.loop_backend.Song.domain.Song;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 실제 셋리스트 등록 리마인드 요구사항:
// - 매일 어제 끝난(종료일 = 어제) 공개 공연의 셋리스트 대상 중 실제 셋리스트가 아직 없는 것을 관리자 디스코드(KOPIS 채널)로 한 통 알린다.
// - 셋리스트 대상: 단독 공연은 출연 정보 1건, 페스티벌은 헤드라이너만.
// - 하루만 알린다: 오늘·그저께 끝난 공연은 담지 않는다. 여러 날 공연은 마지막 날 기준이다.
// - 비공개 공연, 실제 셋리스트를 이미 등록한 출연 정보는 담지 않는다. 대상이 없으면 보내지 않는다.
// - 디스코드 2000자 제한 때문에 10건까지만 나열하고 나머지는 "외 N건".
@DataJpaTest
class ActualSetlistReminderServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final String WEBHOOK = "https://discord.example/webhook/kopis";

    @Autowired private ConcertRepository concertRepository;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private ArtistRepository artistRepository;
    @Autowired private SetlistRepository setlistRepository;
    @Autowired private SongRepository songRepository;
    @Autowired private EntityManager em;

    private DiscordNotificationService discord;
    private ActualSetlistReminderService service;

    @BeforeEach
    void setUp() {
        discord = mock(DiscordNotificationService.class);
        service = new ActualSetlistReminderService(performanceRepository, discord);
        ReflectionTestUtils.setField(service, "webhookUrl", WEBHOOK);
    }

    private Artist artist(String name) {
        return artistRepository.save(Artist.builder().name(name).build());
    }

    private Performance solo(String title, String artistName, LocalDate start, LocalDate end, boolean published) {
        Artist artist = artist(artistName);
        Concert concert = concertRepository.save(Concert.builder()
                .title(title).category(ConcertCategory.J_POP_ARTIST).artist(artist).published(published)
                .startDate(start).endDate(end).build());
        return performanceRepository.save(Performance.solo(concert, artist));
    }

    private Concert festival(String title, LocalDate start, LocalDate end) {
        return concertRepository.save(Concert.builder()
                .title(title).category(ConcertCategory.JAPAN_FESTIVAL).published(true)
                .startDate(start).endDate(end).build());
    }

    private Performance lineup(Concert festival, String artistName, int order, boolean headliner) {
        Performance p = Performance.builder().concert(festival).artist(artist(artistName)).day(1).displayOrder(order).build();
        p.changeHeadliner(headliner);
        return performanceRepository.save(p);
    }

    // 셋리스트는 곡이 1곡 이상 있어야 저장된다
    private void saveSetlist(Performance performance, SetlistType type) {
        Song song = songRepository.save(Song.builder().artist(performance.getArtist())
                .titleOriginal("ドライフラワー").sortOrder(1).build());
        setlistRepository.save(Setlist.builder().performance(performance).type(type)
                .tourName(type == SetlistType.ACTUAL ? null : "Arena Tour")
                .performedOn(type == SetlistType.ACTUAL ? null : LocalDate.of(2026, 5, 1))
                .venueName(type == SetlistType.ACTUAL ? null : "Zepp Haneda")
                .songs(List.of(song)).build());
    }

    private String remindAndCaptureMessage() {
        em.flush();
        em.clear();
        service.remind(TODAY);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(discord).send(eq(WEBHOOK), content.capture());
        return content.getValue();
    }

    @Test
    @DisplayName("어제 끝난 공개 단독 공연에 실제 셋리스트가 없으면 KOPIS 채널로 알린다")
    void remind_SoloEndedYesterdayWithoutActual_SendsReminder() {
        // given
        solo("YUURI LIVE", "Yuuri", YESTERDAY, YESTERDAY, true);

        // when
        String message = remindAndCaptureMessage();

        // then
        assertThat(message).contains("실제 셋리스트 등록 필요 1건");
        assertThat(message).contains("- [Yuuri] YUURI LIVE");
    }

    @Test
    @DisplayName("페스티벌은 헤드라이너만 담고 헤드라이너 표시를 붙인다")
    void remind_Festival_ListsOnlyHeadliners() {
        // given
        Concert summerSonic = festival("SUMMER SONIC 2026", YESTERDAY.minusDays(1), YESTERDAY);
        lineup(summerSonic, "Vaundy", 1, true);
        lineup(summerSonic, "Ado", 2, true);
        lineup(summerSonic, "새소년", 3, false);

        // when
        String message = remindAndCaptureMessage();

        // then
        assertThat(message).contains("실제 셋리스트 등록 필요 2건");
        assertThat(message).contains("- [Vaundy] SUMMER SONIC 2026 (헤드라이너)");
        assertThat(message).contains("- [Ado] SUMMER SONIC 2026 (헤드라이너)");
        assertThat(message).doesNotContain("새소년");
    }

    @Test
    @DisplayName("실제 셋리스트를 이미 등록한 출연 정보는 담지 않고, 지난 셋리스트만 있으면 담는다")
    void remind_WithActualAlreadySaved_ExcludesIt() {
        // given
        Performance done = solo("이미 등록한 공연", "Yuuri", YESTERDAY, YESTERDAY, true);
        saveSetlist(done, SetlistType.ACTUAL);
        Performance pastOnly = solo("지난 셋리스트만 있는 공연", "Vaundy", YESTERDAY, YESTERDAY, true);
        saveSetlist(pastOnly, SetlistType.RECENT);

        // when
        String message = remindAndCaptureMessage();

        // then
        assertThat(message).contains("등록 필요 1건").contains("지난 셋리스트만 있는 공연");
        assertThat(message).doesNotContain("이미 등록한 공연");
    }

    @Test
    @DisplayName("하루만 알린다 - 오늘·그저께 끝난 공연과 비공개 공연은 담지 않는다")
    void remind_OnlyYesterdayAndPublished() {
        // given
        solo("어제 끝난 공연", "Yuuri", YESTERDAY, YESTERDAY, true);
        solo("오늘 끝나는 공연", "Vaundy", TODAY, TODAY, true);
        solo("그저께 끝난 공연", "Ado", YESTERDAY.minusDays(1), YESTERDAY.minusDays(1), true);
        solo("어제 끝난 비공개 공연", "YOASOBI", YESTERDAY, YESTERDAY, false);

        // when
        String message = remindAndCaptureMessage();

        // then
        assertThat(message).contains("등록 필요 1건").contains("어제 끝난 공연");
        assertThat(message).doesNotContain("오늘 끝나는 공연", "그저께 끝난 공연", "비공개 공연");
    }

    @Test
    @DisplayName("여러 날 공연은 마지막 날 다음 날에 알린다 - 첫날이 어제여도 아직 끝나지 않았으면 담지 않는다")
    void remind_MultiDayConcert_UsesEndDate() {
        // given
        solo("어제 시작해 오늘 끝나는 공연", "Yuuri", YESTERDAY, TODAY, true);
        solo("그저께 시작해 어제 끝난 공연", "Vaundy", YESTERDAY.minusDays(1), YESTERDAY, true);

        // when
        String message = remindAndCaptureMessage();

        // then
        assertThat(message).contains("등록 필요 1건").contains("그저께 시작해 어제 끝난 공연");
    }

    @Test
    @DisplayName("대상이 없으면 보내지 않는다")
    void remind_NothingMissing_SendsNothing() {
        // given
        Performance done = solo("이미 등록한 공연", "Yuuri", YESTERDAY, YESTERDAY, true);
        saveSetlist(done, SetlistType.ACTUAL);
        em.flush();
        em.clear();

        // when
        service.remind(TODAY);

        // then
        verify(discord, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("10건을 넘으면 10건까지만 나열하고 나머지는 외 N건으로 줄인다")
    void remind_MoreThanTen_ListsTenAndSummarizesRest() {
        // given
        IntStream.rangeClosed(1, 12).forEach(i -> solo("공연 " + i, "Artist" + i, YESTERDAY, YESTERDAY, true));

        // when
        String message = remindAndCaptureMessage();

        // then
        assertThat(message).contains("등록 필요 12건");
        assertThat(message.lines().filter(l -> l.startsWith("- "))).hasSize(10);
        assertThat(message).endsWith("외 2건");
    }
}
