package com.loop.loop_backend.Notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 디스코드 알림 전송 요구사항:
// - 알림 내용에 사용자 입력이 섞여도 @everyone·@here·유저 멘션이 실제로 울리면 안 된다(멘션은 글자로만 보인다).
// - 웹훅 URL이 설정되지 않았으면 보내지 않는다(디스코드 알림은 선택 기능).
class DiscordNotificationServiceTest {

    private static final String WEBHOOK = "https://discord.example/webhook";

    private RestTemplate restTemplate;
    private DiscordNotificationService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        service = new DiscordNotificationService(restTemplate);
    }

    @Test
    @DisplayName("내용에 @everyone이 있어도 멘션이 울리지 않도록 멘션을 모두 끄고 보낸다")
    @SuppressWarnings("unchecked")
    void send_DisablesAllMentions() {
        // when
        service.send(WEBHOOK, "하고 싶은 말: @everyone 같이 가요");

        // then
        ArgumentCaptor<HttpEntity<Map<String, Object>>> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(WEBHOOK), request.capture(), eq(Void.class));
        Map<String, Object> body = request.getValue().getBody();
        assertThat(body).containsEntry("content", "하고 싶은 말: @everyone 같이 가요");
        assertThat(body).containsEntry("allowed_mentions", Map.of("parse", List.of()));
    }

    @Test
    @DisplayName("웹훅 URL이 비어 있으면 보내지 않는다")
    void send_WithoutWebhook_SendsNothing() {
        // when
        service.send("", "내용");
        service.send(null, "내용");

        // then
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Void.class));
    }
}
