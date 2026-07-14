package com.loop.loop_backend.Chat.repository;

import com.loop.loop_backend.Chat.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChatRoom_IdOrderByCreatedAtAsc(Long roomId);
}
