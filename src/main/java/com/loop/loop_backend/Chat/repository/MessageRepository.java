package com.loop.loop_backend.Chat.repository;

import com.loop.loop_backend.Chat.domain.Message;
import com.loop.loop_backend.Chat.dto.UnreadChatDigestRow;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Slice<Message> findByChatRoom_IdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    // 재가입 컷오프 이후 메시지만 (탈퇴 전 기록 숨김용)
    Slice<Message> findByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(Long roomId, LocalDateTime after, Pageable pageable);

    Optional<Message> findTopByChatRoom_IdOrderByCreatedAtDesc(Long roomId);

    Optional<Message> findTopByChatRoom_IdAndCreatedAtAfterOrderByCreatedAtDesc(Long roomId, LocalDateTime after);

    long countByChatRoom_IdAndSender_IdNotAndIsReadFalse(Long roomId, Long senderId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.chatRoom.id = :roomId AND m.sender.id <> :userId AND m.isRead = false")
    void markAllAsRead(@Param("roomId") Long roomId, @Param("userId") Long userId);

    @Query("""
            SELECT new com.loop.loop_backend.Chat.dto.UnreadChatDigestRow(
                cp.user.id,
                cp.user.email,
                cp.user.nickname,
                m.chatRoom.id,
                m.sender.nickname,
                COUNT(m),
                MAX(m.createdAt)
            )
            FROM Message m, ChatParticipant cp
            WHERE cp.chatRoom = m.chatRoom
              AND cp.user.id <> m.sender.id
              AND cp.status = com.loop.loop_backend.Chat.domain.ParticipantStatus.ACTIVE
              AND m.chatRoom.type = com.loop.loop_backend.Chat.domain.ChatRoomType.DIRECT
              AND m.type = com.loop.loop_backend.Chat.domain.MessageType.USER
              AND m.isRead = false
              AND m.createdAt < :cutoff
              AND cp.user.email IS NOT NULL
              AND cp.user.status = com.loop.loop_backend.User.domain.Status.ACTIVE
              AND cp.user.chatNotificationEmail = true
            GROUP BY cp.user.id, cp.user.email, cp.user.nickname, m.chatRoom.id, m.sender.nickname
            ORDER BY cp.user.id, MAX(m.createdAt) DESC
            """)
    List<UnreadChatDigestRow> findDailyUnreadDigest(@Param("cutoff") LocalDateTime cutoff);
}
