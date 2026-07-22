package com.loop.loop_backend.Chat.service;

import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.dto.ChatMessagesResponseDto;
import com.loop.loop_backend.Chat.dto.ChatRoomResponseDto;
import com.loop.loop_backend.Chat.dto.CreateChatRoomRequestDto;
import com.loop.loop_backend.Chat.dto.StartDirectChatRequestDto;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatService {

    ChatRoomResponseDto createRoom(Long requesterId, CreateChatRoomRequestDto request);

    ChatRoomResponseDto startDirectChat(Long myUserId, StartDirectChatRequestDto request);

    ChatRoomResponseDto joinRoom(Long roomId, Long userId);

    void leaveRoom(Long roomId, Long userId);

    List<ChatRoomResponseDto> getMyRooms(Long userId);

    ChatMessagesResponseDto getMessages(Long roomId, Long userId, Pageable pageable);

    ChatMessageDto saveMessage(Long roomId, Long senderId, String rawContent);

    void markAsRead(Long roomId, Long userId);

    void hideDirectRoomForUser(Long actorUserId, Long otherUserId);

    void handleUserWithdrawn(Long withdrawnUserId);
}
