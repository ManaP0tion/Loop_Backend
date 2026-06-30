package com.loop.loop_backend.Chat.repository;

import com.loop.loop_backend.Chat.domain.ChatParticipant;
import com.loop.loop_backend.Chat.domain.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findByChatRoom_IdAndUser_Id(Long roomId, Long userId);

    List<ChatParticipant> findByChatRoom_IdAndStatus(Long roomId, ParticipantStatus status);

    List<ChatParticipant> findByUser_IdAndStatus(Long userId, ParticipantStatus status);

    boolean existsByChatRoom_IdAndUser_IdAndStatus(Long roomId, Long userId, ParticipantStatus status);
}
