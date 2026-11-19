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

    long getUnreadCount(Long userId);

    ChatMessagesResponseDto getMessages(Long roomId, Long userId, Pageable pageable);

    ChatMessageDto saveMessage(Long roomId, Long senderId, String rawContent);

    // 이미지 1~5장을 장마다 IMAGE 메시지로 저장한다. 한 번에 보낸 메시지는 같은 imageGroupId를 가진다
    List<ChatMessageDto> saveImageMessages(Long roomId, Long senderId, List<org.springframework.web.multipart.MultipartFile> images);

    void markAsRead(Long roomId, Long userId);

    void hideDirectRoomForUser(Long actorUserId, Long otherUserId);

    void handleUserWithdrawn(Long withdrawnUserId);
}
