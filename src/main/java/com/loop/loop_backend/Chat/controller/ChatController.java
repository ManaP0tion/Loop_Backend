package com.loop.loop_backend.Chat.controller;

import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    @MessageMapping("/chat/message")
    public void sendMessage(@Payload @Valid ChatMessageDto message, Principal principal) {
        if (principal == null) {
            log.warn("인증되지 않은 STOMP SEND 시도");
            return;
        }
        Long senderId = Long.parseLong(principal.getName());
        ChatMessageDto persisted = chatService.saveMessage(message.getRoomId(), senderId, message.getContent());
        messagingTemplate.convertAndSend("/sub/chat/room/" + persisted.getRoomId(), persisted);
    }
}
