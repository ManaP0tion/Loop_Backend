package com.loop.loop_backend.Chat.controller;

import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    @MessageMapping("/chat/message")
    public void sendMessage(ChatMessageDto message) {
        /*if (!chatService.canChat(message.getSenderId(), message.getRoomId())) {
            log.warn("채팅 차단: senderId={}, roomId={}", message.getSenderId(), message.getRoomId());
            return;
        }*/

        message = message.toBuilder().createdAt(LocalDateTime.now()).build();

        chatService.saveMessage(message);
        messagingTemplate.convertAndSend("/sub/chat/room/" + message.getRoomId(), message);
    }
}
