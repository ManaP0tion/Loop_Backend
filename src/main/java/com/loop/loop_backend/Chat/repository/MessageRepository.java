package com.loop.loop_backend.Chat.repository;

import com.loop.loop_backend.Chat.domain.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Slice<Message> findByChatRoom_IdOrderByCreatedAtDesc(Long roomId, Pageable pageable);

    Optional<Message> findTopByChatRoom_IdOrderByCreatedAtDesc(Long roomId);

    long countByChatRoom_IdAndSender_IdNotAndIsReadFalse(Long roomId, Long senderId);

    @Modifying
    @Query("UPDATE Message m SET m.isRead = true WHERE m.chatRoom.id = :roomId AND m.sender.id <> :userId AND m.isRead = false")
    void markAllAsRead(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
