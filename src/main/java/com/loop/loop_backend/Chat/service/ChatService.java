package com.loop.loop_backend.Chat.service;

import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.dto.ChatRoomResponseDto;
import com.loop.loop_backend.Chat.dto.CreateChatRoomRequestDto;
import com.loop.loop_backend.Chat.dto.StartDirectChatRequestDto;

import java.util.List;

public interface ChatService {

    ChatRoomResponseDto createRoom(CreateChatRoomRequestDto request);

    ChatRoomResponseDto startDirectChat(StartDirectChatRequestDto request);

    ChatRoomResponseDto joinRoom(Long roomId, Long userId);

    void leaveRoom(Long roomId, Long userId);

    List<ChatRoomResponseDto> getRoomsByUser(Long userId);

    List<ChatMessageDto> getMessages(Long roomId);

    boolean canChat(Long senderId, Long roomId);

    void saveMessage(ChatMessageDto dto);

    void markAsRead(Long roomId, Long userId);
}
