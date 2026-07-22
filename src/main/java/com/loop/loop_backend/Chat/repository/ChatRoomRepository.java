package com.loop.loop_backend.Chat.repository;

import com.loop.loop_backend.Chat.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    boolean existsByPost_Id(Long postId);

    @Query("""
            SELECT cp.chatRoom FROM ChatParticipant cp
            WHERE cp.user.id = :userId
            AND cp.status = com.loop.loop_backend.Chat.domain.ParticipantStatus.ACTIVE
            """)
    List<ChatRoom> findActiveRoomsByUserId(@Param("userId") Long userId);

    // 두 유저 사이에 이미 존재하는 DIRECT 채팅방 조회
    @Query("""
            SELECT cr FROM ChatRoom cr
            WHERE cr.type = com.loop.loop_backend.Chat.domain.ChatRoomType.DIRECT
            AND EXISTS (
                SELECT cp1 FROM ChatParticipant cp1
                WHERE cp1.chatRoom = cr
                AND cp1.user.id = :userId1
                AND cp1.status = com.loop.loop_backend.Chat.domain.ParticipantStatus.ACTIVE
            )
            AND EXISTS (
                SELECT cp2 FROM ChatParticipant cp2
                WHERE cp2.chatRoom = cr
                AND cp2.user.id = :userId2
                AND cp2.status = com.loop.loop_backend.Chat.domain.ParticipantStatus.ACTIVE
            )
            """)
    Optional<ChatRoom> findDirectRoomBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    // 참여자 상태와 무관하게 두 유저 사이 DIRECT 방을 찾는다. (차단/신고로 방을 hide 할 때 대상 방을 찾기 위함)
    @Query("""
            SELECT cr FROM ChatRoom cr
            WHERE cr.type = com.loop.loop_backend.Chat.domain.ChatRoomType.DIRECT
            AND EXISTS (SELECT 1 FROM ChatParticipant p1 WHERE p1.chatRoom = cr AND p1.user.id = :userId1)
            AND EXISTS (SELECT 1 FROM ChatParticipant p2 WHERE p2.chatRoom = cr AND p2.user.id = :userId2)
            """)
    Optional<ChatRoom> findDirectRoomBetweenAnyStatus(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
