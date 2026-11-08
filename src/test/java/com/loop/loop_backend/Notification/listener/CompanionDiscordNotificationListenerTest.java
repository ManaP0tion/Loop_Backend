package com.loop.loop_backend.Notification.listener;

import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.event.CompanionCreatedEvent;
import com.loop.loop_backend.Notification.service.DiscordNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// 동행 프로필 생성 디스코드 알림 요구사항:
// - 동행 프로필이 생기면 동행 채널에 한 통 보낸다(현황 파악용).
// - 메시지에는 프로필 ID, 공연명, 관람일(DAY와 날짜), 작성자 닉네임, 동행에게 하고 싶은 말이 담긴다.
// - 공연 날짜가 미정이면 관람일은 DAY만, 하고 싶은 말이 없으면 "-".
class CompanionDiscordNotificationListenerTest {

    private static final String WEBHOOK = "https://discord.example/webhook/companion";

    private DiscordNotificationService discord;
    private CompanionDiscordNotificationListener listener;

    @BeforeEach
    void setUp() {
        discord = mock(DiscordNotificationService.class);
        listener = new CompanionDiscordNotificationListener(discord);
        ReflectionTestUtils.setField(listener, "webhookUrl", WEBHOOK);
    }

    private String sentMessage() {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(discord).send(eq(WEBHOOK), content.capture());
        return content.getValue();
    }

    @Test
    @DisplayName("동행 프로필이 생기면 동행 채널에 공연명·관람일·작성자·하고 싶은 말을 보낸다")
    void handleCompanionCreated_SendsSummaryToCompanionChannel() {
        // when
        listener.handleCompanionCreated(new CompanionCreatedEvent(12L, "YOASOBI ASIA TOUR 2026",
                WatchDay.DAY2, LocalDate.of(2026, 11, 4), "새벽베텔기우스", "같이 즐겁게 봐요!"));

        // then
        assertThat(sentMessage()).isEqualTo("""
                **새 동행 프로필** (ID: 12)
                공연: YOASOBI ASIA TOUR 2026
                관람일: DAY2 (2026.11.04)
                작성자: 새벽베텔기우스
                하고 싶은 말: 같이 즐겁게 봐요!""");
    }

    @Test
    @DisplayName("공연 날짜가 미정이면 관람일은 DAY만, 하고 싶은 말이 없으면 -로 보낸다")
    void handleCompanionCreated_UndatedConcertAndNoMessage() {
        // when
        listener.handleCompanionCreated(new CompanionCreatedEvent(13L, "SUMMER SONIC 2026",
                WatchDay.DAY1, null, "달빛고래", null));

        // then
        String message = sentMessage();
        assertThat(message).contains("관람일: DAY1\n");
        assertThat(message).endsWith("하고 싶은 말: -");
    }

    @Test
    @DisplayName("하고 싶은 말이 공백뿐이어도 -로 보낸다")
    void handleCompanionCreated_BlankMessage() {
        // when
        listener.handleCompanionCreated(new CompanionCreatedEvent(14L, "공연", WatchDay.DAY1, null, "달빛고래", "   "));

        // then
        assertThat(sentMessage()).endsWith("하고 싶은 말: -");
    }
}
