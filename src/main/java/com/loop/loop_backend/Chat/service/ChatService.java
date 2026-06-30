package com.loop.loop_backend.Chat.service;

import com.loop.loop_backend.Chat.dto.ChatRoomResponseDto;
import com.loop.loop_backend.Chat.dto.CreateChatRoomRequestDto;
import com.loop.loop_backend.Chat.dto.StartDirectChatRequestDto;

import java.util.List;

public interface ChatService {

    // 동행 신청 기반 채팅방 생성 (CompanionPost + host + applicant)
    ChatRoomResponseDto createRoom(CreateChatRoomRequestDto request);

    // 1:1 채팅 시작 - 이미 방이 있으면 기존 방 반환, 없으면 생성
    ChatRoomResponseDto startDirectChat(StartDirectChatRequestDto request);

    // GROUP 채팅방 참여 (새 참여자 추가)
    ChatRoomResponseDto joinRoom(Long roomId, Long userId);

    void leaveRoom(Long roomId, Long userId);

    List<ChatRoomResponseDto> getRoomsByUser(Long userId);

    List<?> getMessages(Long roomId);

    boolean canChat(Long senderId, Long roomId);
}
