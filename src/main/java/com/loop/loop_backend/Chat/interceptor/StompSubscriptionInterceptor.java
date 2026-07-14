package com.loop.loop_backend.Chat.interceptor;

import com.loop.loop_backend.Chat.domain.ParticipantStatus;
import com.loop.loop_backend.Chat.repository.ChatParticipantRepository;
import com.loop.loop_backend.common.jwt.JwtTokenProvider;
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

    private final ChatParticipantRepository chatParticipantRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        // CONNECT: Authorization 헤더로 토큰 인증 후 Principal 세팅
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtTokenProvider.validateToken(token)) {
                    Long userId = jwtTokenProvider.getUserId(token);
                    accessor.setUser(() -> userId.toString());
                }
            }
            return message;
        }

        // SUBSCRIBE: 채팅방 참여자 검증
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/sub/chat/room/")) {
                return message;
            }

            Principal user = accessor.getUser();
            if (user == null) {
                throw new MessagingException("채팅방 구독은 로그인이 필요합니다.");
            }

            Long userId;
            Long roomId;
            try {
                userId = Long.parseLong(user.getName());
                roomId = Long.parseLong(destination.substring("/sub/chat/room/".length()));
            } catch (NumberFormatException e) {
                throw new MessagingException("잘못된 구독 요청입니다.");
            }

            if (!chatParticipantRepository.existsByChatRoom_IdAndUser_IdAndStatus(roomId, userId, ParticipantStatus.ACTIVE)) {
                log.warn("채팅방 구독 차단: userId={}, roomId={}", userId, roomId);
                throw new MessagingException("해당 채팅방의 참여자가 아닙니다.");
            }
        }

        return message;
    }
}
