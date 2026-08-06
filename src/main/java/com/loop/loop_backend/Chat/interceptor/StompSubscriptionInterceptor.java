package com.loop.loop_backend.Chat.interceptor;

import com.loop.loop_backend.Chat.domain.ParticipantStatus;
import com.loop.loop_backend.Chat.repository.ChatParticipantRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.jwt.TokenAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompSubscriptionInterceptor implements ChannelInterceptor {

    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/room/";
    private static final String ERROR_DESTINATION_PREFIX = "/sub/chat/errors/";
    private static final String APP_DESTINATION_PREFIX = "/pub/";

    private final ChatParticipantRepository chatParticipantRepository;
    private final TokenAuthenticator tokenAuthenticator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == null) return message;

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            case SEND -> handleSend(accessor);
            default -> { /* no-op */ }
        }

        return message;
    }

    // REST 필터와 동일한 규칙(토큰 타입·블랙리스트·계정 상태)을 TokenAuthenticator 로 공유한다.
    // 로그아웃한 토큰이나 정지된 계정이 채팅만 계속 쓰는 구멍이 생기지 않도록.
    private void handleConnect(StompHeaderAccessor accessor) {
        String token = TokenAuthenticator.stripBearer(accessor.getFirstNativeHeader("Authorization"));
        if (token == null) {
            throw new MessagingException("인증 토큰이 필요합니다.");
        }
        Long userId;
        try {
            userId = tokenAuthenticator.authenticate(token)
                    .orElseThrow(() -> new MessagingException("유효하지 않은 토큰입니다."))
                    .getId();
        } catch (BusinessException e) {
            throw new MessagingException(e.getErrorCode().getMessage());
        }
        accessor.setUser(() -> userId.toString());
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        if (destination.startsWith(ROOM_DESTINATION_PREFIX)) {
            Long userId = requireUserId(accessor, "채팅방 구독은 로그인이 필요합니다.");
            Long roomId;
            try {
                roomId = Long.parseLong(destination.substring(ROOM_DESTINATION_PREFIX.length()));
            } catch (NumberFormatException e) {
                throw new MessagingException("잘못된 구독 요청입니다.");
            }
            if (!chatParticipantRepository.existsByChatRoom_IdAndUser_IdAndStatus(roomId, userId, ParticipantStatus.ACTIVE)) {
                log.warn("채팅방 구독 차단: userId={}, roomId={}", userId, roomId);
                throw new MessagingException("해당 채팅방의 참여자가 아닙니다.");
            }
        } else if (destination.startsWith(ERROR_DESTINATION_PREFIX)) {
            Long userId = requireUserId(accessor, "에러 채널 구독은 로그인이 필요합니다.");
            Long targetUserId;
            try {
                targetUserId = Long.parseLong(destination.substring(ERROR_DESTINATION_PREFIX.length()));
            } catch (NumberFormatException e) {
                throw new MessagingException("잘못된 구독 요청입니다.");
            }
            if (!userId.equals(targetUserId)) {
                log.warn("에러 채널 무단 구독 차단: userId={}, targetUserId={}", userId, targetUserId);
                throw new MessagingException("본인의 에러 채널만 구독할 수 있습니다.");
            }
        }
    }

    private void handleSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith(APP_DESTINATION_PREFIX)) {
            return;
        }
        requireUserId(accessor, "메시지 전송은 로그인이 필요합니다.");
    }

    private Long requireUserId(StompHeaderAccessor accessor, String message) {
        Principal user = accessor.getUser();
        if (user == null) {
            throw new MessagingException(message);
        }
        try {
            return Long.parseLong(user.getName());
        } catch (NumberFormatException e) {
            throw new MessagingException("잘못된 인증 정보입니다.");
        }
    }
}
