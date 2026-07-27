package com.loop.loop_backend.Chat.controller;

import com.loop.loop_backend.Chat.dto.ChatErrorDto;
import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.service.ChatService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
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

    // STOMP 로 들어온 SEND 처리 중 던진 BusinessException 을 발신자 개인 채널로 되돌려서 프론트가 토스트로 표시할 수 있게 한다.
    @MessageExceptionHandler(BusinessException.class)
    public void handleBusinessError(BusinessException ex, Principal principal, @Payload(required = false) ChatMessageDto message) {
        if (principal == null) return;
        ErrorCode code = ex.getErrorCode();
        Long roomId = message != null ? message.getRoomId() : null;
        messagingTemplate.convertAndSend(
                "/sub/chat/errors/" + principal.getName(),
                new ChatErrorDto(code.name(), code.getMessage(), roomId));
    }
}
