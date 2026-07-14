package com.loop.loop_backend.Chat.controller;

import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.dto.ChatRoomResponseDto;
import com.loop.loop_backend.Chat.dto.CreateChatRoomRequestDto;
import com.loop.loop_backend.Chat.dto.StartDirectChatRequestDto;
import com.loop.loop_backend.Chat.service.ChatService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅 REST API")
public class ChatRoomController {

    private final ChatService chatService;

    @PostMapping("/rooms")
    @Operation(summary = "채팅방 생성", description = "동행 신청 시 채팅방 + 참여자 생성")
    public ResponseEntity<CommonResponse<ChatRoomResponseDto>> createRoom(
            @RequestBody @Valid CreateChatRoomRequestDto request) {
        return ResponseEntity.ok(CommonResponse.success(chatService.createRoom(request)));
    }

    @PostMapping("/direct")
    @Operation(summary = "1:1 채팅 시작", description = "두 유저 사이 DIRECT 채팅방 생성 또는 기존 방 반환")
    public ResponseEntity<CommonResponse<ChatRoomResponseDto>> startDirectChat(
            @RequestBody @Valid StartDirectChatRequestDto request) {
        return ResponseEntity.ok(CommonResponse.success(chatService.startDirectChat(request)));
    }

    @PostMapping("/rooms/{roomId}/join")
    @Operation(summary = "채팅방 참여", description = "GROUP 채팅방에 새 참여자로 입장")
    public ResponseEntity<CommonResponse<ChatRoomResponseDto>> joinRoom(
            @PathVariable Long roomId, @RequestParam Long userId) {
        return ResponseEntity.ok(CommonResponse.success(chatService.joinRoom(roomId, userId)));
    }

    @GetMapping("/rooms")
    @Operation(summary = "채팅방 목록 조회 (전체)")
    public ResponseEntity<CommonResponse<List<ChatRoomResponseDto>>> getRooms() {
        return ResponseEntity.ok(CommonResponse.success(chatService.getRoomsByUser(null)));
    }

    @GetMapping("/rooms/user/{userId}")
    @Operation(summary = "내 채팅방 목록 조회", description = "ACTIVE 상태인 채팅방만 반환")
    public ResponseEntity<CommonResponse<List<ChatRoomResponseDto>>> getUserRooms(@PathVariable Long userId) {
        return ResponseEntity.ok(CommonResponse.success(chatService.getRoomsByUser(userId)));
    }

    @PatchMapping("/rooms/{roomId}/read")
    @Operation(summary = "메시지 읽음 처리", description = "해당 채팅방에서 상대방이 보낸 안읽은 메시지를 모두 읽음으로 처리")
    public ResponseEntity<CommonResponse<Void>> markAsRead(
            @PathVariable Long roomId, @RequestParam Long userId) {
        chatService.markAsRead(roomId, userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @PatchMapping("/rooms/{roomId}/leave")
    @Operation(summary = "채팅방 나가기")
    public ResponseEntity<CommonResponse<Void>> leaveRoom(
            @PathVariable Long roomId, @RequestParam Long userId) {
        chatService.leaveRoom(roomId, userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "채팅 메시지 조회")
    public ResponseEntity<CommonResponse<List<ChatMessageDto>>> getMessages(@PathVariable Long roomId) {
        return ResponseEntity.ok(CommonResponse.success(chatService.getMessages(roomId)));
    }
}
