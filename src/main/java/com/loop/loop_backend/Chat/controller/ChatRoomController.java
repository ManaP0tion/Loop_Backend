package com.loop.loop_backend.Chat.controller;

import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.dto.ChatMessagesResponseDto;
import com.loop.loop_backend.Chat.dto.ChatRoomResponseDto;
import com.loop.loop_backend.Chat.dto.CreateChatRoomRequestDto;
import com.loop.loop_backend.Chat.dto.StartDirectChatRequestDto;
import com.loop.loop_backend.Chat.service.ChatService;
import com.loop.loop_backend.common.exception.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "채팅 REST API")
public class ChatRoomController {

    private final ChatService chatService;

//    @PostMapping("/rooms")
//    @Operation(summary = "채팅방 생성", description = "동행 게시글 신청자가 host와의 채팅방을 생성합니다. 요청자는 신청자로 참여합니다.")
//    public ResponseEntity<CommonResponse<ChatRoomResponseDto>> createRoom(
//            @AuthenticationPrincipal Long userId,
//            @RequestBody @Valid CreateChatRoomRequestDto request) {
//        return ResponseEntity.ok(CommonResponse.success(chatService.createRoom(userId, request)));
//    }

    @PostMapping("/direct")
    @Operation(summary = "1:1 채팅 시작", description = "요청자와 targetUserId 사이 DIRECT 채팅방을 생성하거나 기존 방을 반환합니다.")
    public ResponseEntity<CommonResponse<ChatRoomResponseDto>> startDirectChat(
            @AuthenticationPrincipal Long userId,
            @RequestBody @Valid StartDirectChatRequestDto request) {
        return ResponseEntity.ok(CommonResponse.success(chatService.startDirectChat(userId, request)));
    }

    @PostMapping("/rooms/{roomId}/join")
    @Operation(summary = "채팅방 참여", description = "GROUP 채팅방에 요청자를 새 참여자로 추가합니다.")
    public ResponseEntity<CommonResponse<ChatRoomResponseDto>> joinRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId) {
        return ResponseEntity.ok(CommonResponse.success(chatService.joinRoom(roomId, userId)));
    }

    @GetMapping("/rooms/me")
    @Operation(summary = "내 채팅방 목록 조회", description = "ACTIVE 상태인 내 채팅방만 반환")
    public ResponseEntity<CommonResponse<List<ChatRoomResponseDto>>> getMyRooms(
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(CommonResponse.success(chatService.getMyRooms(userId)));
    }

    @PatchMapping("/rooms/{roomId}/read")
    @Operation(summary = "메시지 읽음 처리", description = "해당 채팅방의 안읽은 메시지를 모두 읽음으로 처리합니다.")
    public ResponseEntity<CommonResponse<Void>> markAsRead(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId) {
        chatService.markAsRead(roomId, userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @PatchMapping("/rooms/{roomId}/leave")
    @Operation(summary = "채팅방 나가기")
    public ResponseEntity<CommonResponse<Void>> leaveRoom(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId) {
        chatService.leaveRoom(roomId, userId);
        return ResponseEntity.ok(CommonResponse.success(null));
    }

    @PostMapping(value = "/rooms/{roomId}/images", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "채팅 이미지 전송",
            description = "이미지를 비공개 버킷에 업로드하고 IMAGE 메시지로 저장한 뒤, 해당 방 구독자에게 실시간 브로드캐스트합니다. 참여자만 조회 가능한 presigned URL로 응답.")
    public ResponseEntity<CommonResponse<ChatMessageDto>> sendImage(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @RequestPart("image") org.springframework.web.multipart.MultipartFile image) {
        return ResponseEntity.ok(CommonResponse.success(chatService.saveImageMessage(roomId, userId, image)));
    }

    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "채팅 메시지 조회",
            description = "요청자가 참여자인 경우에만 조회 가능. 최신순 페이지네이션. DIRECT 방의 경우 상대방 관계 상태(탈퇴/차단/신고)를 함께 반환.")
    public ResponseEntity<CommonResponse<ChatMessagesResponseDto>> getMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long roomId,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(CommonResponse.success(chatService.getMessages(roomId, userId, pageable)));
    }
}
