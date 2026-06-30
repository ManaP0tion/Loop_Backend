package com.loop.loop_backend.Chat.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatService chatService;

    @MessageMapping("/chat/message")
    public void sendMessage(ChatMessageDto message) {
        if (!chatService.canChat(message.getSenderId(), message.getRoomId())) {
            log.warn("채팅 차단: senderId={}, roomId={}", message.getSenderId(), message.getRoomId());
            return;
        }

        message = message.toBuilder().createdAt(LocalDateTime.now()).build();

        // 1. 방별 메시지 캐싱 (조회용)
        redisTemplate.opsForList().rightPush("chat:room:" + message.getRoomId() + ":messages", message);

        // 2. DB 저장용 대기열 큐
        redisTemplate.opsForList().rightPush("chat:save_queue", message);

        // 3. Redis Pub/Sub 발행 → RedisSubscriber가 수신 후 WebSocket 클라이언트에 전달
        try {
            String json = objectMapper.writeValueAsString(message);
            stringRedisTemplate.convertAndSend("chat:room:" + message.getRoomId(), json);
        } catch (JsonProcessingException e) {
            log.error("채팅 메시지 직렬화 실패", e);
        }
    }
}
