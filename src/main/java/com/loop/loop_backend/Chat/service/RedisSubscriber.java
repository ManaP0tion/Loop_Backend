package com.loop.loop_backend.Chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        // chat:room:* 패턴의 모든 채널 구독
        listenerContainer.addMessageListener(this, new PatternTopic("chat:room:*"));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatMessageDto dto = objectMapper.readValue(json, ChatMessageDto.class);
            // SimpleBroker를 통해 해당 채팅방 구독자들에게 전달
            messagingTemplate.convertAndSend("/sub/chat/room/" + dto.getRoomId(), dto);
        } catch (IOException e) {
            log.error("Redis 채팅 메시지 처리 실패: {}", e.getMessage());
        }
    }
}
