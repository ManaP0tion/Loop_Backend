package com.loop.loop_backend.Notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordNotificationService {

    private final RestTemplate restTemplate;

    // webhookUrl이 비어있으면(미설정) 전송을 건너뜀 - 디스코드 알림은 선택 기능
    public void send(String webhookUrl, String content) {
        if (!StringUtils.hasText(webhookUrl)) {
            log.warn("디스코드 웹훅 URL이 설정되지 않아 알림을 건너뜁니다.");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 사용자 입력(신고 상세, 동행에게 하고 싶은 말 등)의 @everyone·@here·유저 멘션이 실제로 울리지 않게 멘션을 모두 끈다
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                Map.of("content", content, "allowed_mentions", Map.of("parse", List.of())), headers);

        try {
            restTemplate.postForEntity(webhookUrl, request, Void.class);
        } catch (RestClientException e) {
            log.error("디스코드 웹훅 전송 실패", e);
        }
    }
}