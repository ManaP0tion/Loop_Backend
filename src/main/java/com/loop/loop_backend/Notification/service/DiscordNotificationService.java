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

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordNotificationService {

    private final RestTemplate restTemplate;

    // webhookUrl이 비어있으면(미설정) 조용히 스킵 - 디스코드 알림은 선택 기능
    public void send(String webhookUrl, String content) {
        if (!StringUtils.hasText(webhookUrl)) {
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("content", content), headers);

        try {
            restTemplate.postForEntity(webhookUrl, request, Void.class);
        } catch (RestClientException e) {
            log.error("디스코드 웹훅 전송 실패", e);
        }
    }
}