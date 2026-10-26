package com.loop.loop_backend.Notification.listener;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.ConcertImport.event.KopisSyncCompletedEvent;
import com.loop.loop_backend.ConcertImport.event.KopisSyncCompletedEvent.NewImport;
import com.loop.loop_backend.ConcertImport.kopis.SyncTrigger;
import com.loop.loop_backend.Notification.service.DiscordNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

// KOPIS 신규 수집 디스코드 알림 요구사항:
// - 매일 자동 실행(09:00)으로 새 공연이 들어왔을 때만 KOPIS 채널에 한 통 보낸다.
// - 새 건이 없으면 실패가 있어도 보내지 않는다(임시: 웹훅 연결 확인 기간에는 0건도 보낸다). 어드민 수동 실행은 보내지 않는다.
// - 메시지에는 건수와 목록(아티스트 또는 페스티벌, 공연명, 기간)이 담긴다.
// - 디스코드 2000자 제한 때문에 10건까지만 나열하고 나머지는 "외 N건", 긴 공연명은 줄인다.
class KopisDiscordNotificationListenerTest {

    private static final String WEBHOOK = "https://discord.example/webhook/kopis";

    private DiscordNotificationService discord;
    private KopisDiscordNotificationListener listener;

    @BeforeEach
    void setUp() {
        discord = mock(DiscordNotificationService.class);
        listener = new KopisDiscordNotificationListener(discord);
        ReflectionTestUtils.setField(listener, "webhookUrl", WEBHOOK);
    }

    private static NewImport solo(String artist, String title) {
        return new NewImport(title, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4),
                ConcertCategory.J_POP_ARTIST, List.of(artist));
    }

    private static NewImport festival(String title) {
        return new NewImport(title, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 16),
                ConcertCategory.JAPAN_FESTIVAL, List.of());
    }

    private String sentMessage() {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(discord).send(eq(WEBHOOK), content.capture());
        return content.getValue();
    }

    @Test
    @DisplayName("자동 실행으로 새 공연이 들어오면 KOPIS 채널에 건수와 목록을 한 통으로 보낸다")
    void handleSyncCompleted_ScheduledWithNewImports_SendsOneMessageWithCountAndList() {
        // when
        listener.handleSyncCompleted(new KopisSyncCompletedEvent(SyncTrigger.SCHEDULED, List.of(
                solo("YOASOBI", "YOASOBI ASIA TOUR 2026"),
                festival("SUMMER SONIC 2026")), 0));

        // then
        String message = sentMessage();
        assertThat(message).contains("신규 수집 2건");
        assertThat(message).contains("- [YOASOBI] YOASOBI ASIA TOUR 2026 (2026.11.03~2026.11.04)");
        assertThat(message).contains("- [페스티벌] SUMMER SONIC 2026 (2026.08.15~2026.08.16)");
    }

    // TODO(임시): 웹훅 연결 확인 기간에는 0건도 보낸다. 되돌릴 때 이 테스트를 아래 원래 요구사항으로 바꾼다.
    //   @DisplayName("새 공연이 없으면 실패가 있어도 보내지 않는다") → verify(discord, never()).send(anyString(), anyString());
    @Test
    @DisplayName("(임시) 자동 실행에서 새 공연이 없어도 연결 확인용으로 0건과 실패 수를 보낸다")
    void handleSyncCompleted_NoNewImports_SendsEmptyMessageTemporarily() {
        // when
        listener.handleSyncCompleted(new KopisSyncCompletedEvent(SyncTrigger.SCHEDULED, List.of(), 3));

        // then
        assertThat(sentMessage()).isEqualTo("**KOPIS 자동 수집 완료** 신규 0건 (실패 3건)");
    }

    @Test
    @DisplayName("어드민 수동 실행은 새 공연이 없을 때도 보내지 않는다")
    void handleSyncCompleted_ManualWithoutNewImports_SendsNothing() {
        // when
        listener.handleSyncCompleted(new KopisSyncCompletedEvent(SyncTrigger.MANUAL, List.of(), 0));

        // then
        verify(discord, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("어드민 수동 실행은 새 공연이 있어도 보내지 않는다")
    void handleSyncCompleted_Manual_SendsNothing() {
        // when
        listener.handleSyncCompleted(new KopisSyncCompletedEvent(SyncTrigger.MANUAL,
                List.of(solo("YOASOBI", "YOASOBI ASIA TOUR 2026")), 0));

        // then
        verify(discord, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("10건을 넘으면 10건까지만 나열하고 나머지는 외 N건으로 줄인다")
    void handleSyncCompleted_MoreThanTen_ListsTenAndSummarizesRest() {
        // given
        List<NewImport> imports = IntStream.rangeClosed(1, 13)
                .mapToObj(i -> solo("Artist" + i, "공연 " + i)).toList();

        // when
        listener.handleSyncCompleted(new KopisSyncCompletedEvent(SyncTrigger.SCHEDULED, imports, 0));

        // then
        String message = sentMessage();
        assertThat(message).contains("신규 수집 13건");
        assertThat(message.lines().filter(l -> l.startsWith("- "))).hasSize(10);
        assertThat(message).contains("공연 10 ").doesNotContain("공연 11 ");
        assertThat(message).endsWith("외 3건");
    }

    @Test
    @DisplayName("공연 기간은 하루면 날짜 하나, 모르면 날짜 미정으로 적고, 아티스트가 없는 내한 공연은 표시 없이 적는다")
    void handleSyncCompleted_FormatsPeriodAndLabel() {
        // when
        listener.handleSyncCompleted(new KopisSyncCompletedEvent(SyncTrigger.SCHEDULED, List.of(
                new NewImport("하루 공연", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3),
                        ConcertCategory.J_POP_ARTIST, List.of("Vaundy")),
                new NewImport("날짜 미정 공연", null, null, ConcertCategory.J_POP_ARTIST, List.of())), 0));

        // then
        String message = sentMessage();
        assertThat(message).contains("- [Vaundy] 하루 공연 (2026.11.03)");
        assertThat(message).contains("- 날짜 미정 공연 (날짜 미정)");
    }

    @Test
    @DisplayName("최대 10건에 긴 공연명이어도 디스코드 2000자 제한을 넘지 않는다")
    void handleSyncCompleted_LongTitles_StaysWithinDiscordLimit() {
        // given - KOPIS 공연명 컬럼 최대 길이(255자)
        List<NewImport> imports = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> solo("Artist" + i, "가".repeat(255))).toList();

        // when
        listener.handleSyncCompleted(new KopisSyncCompletedEvent(SyncTrigger.SCHEDULED, imports, 0));

        // then
        assertThat(sentMessage().length()).isLessThanOrEqualTo(2000);
    }
}
