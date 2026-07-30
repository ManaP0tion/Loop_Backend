package com.loop.loop_backend.Chat.repository;

import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.dto.ChatRoomSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    boolean existsByPost_Id(Long postId);

    // 대시보드: ACTIVE 참여자가 1명 이상 남은 방(둘 다 나가면 죽은 방으로 간주)
    @Query("""
            SELECT COUNT(DISTINCT cp.chatRoom.id) FROM ChatParticipant cp
            WHERE cp.status = com.loop.loop_backend.Chat.domain.ParticipantStatus.ACTIVE
            """)
    long countActiveRooms();

    // 트랜잭션 밖에서 응답을 조립해야 할 때(startDirectChat) 지연로딩 없이 필요한 값만 한 번에 조회
    @Query("""
            SELECT new com.loop.loop_backend.Chat.dto.ChatRoomSummaryDto(
                cr.id, cr.name, cr.type, cr.createdAt, p.id, c.id)
            FROM ChatRoom cr
            LEFT JOIN cr.post p
            LEFT JOIN p.concert c
            WHERE cr.id = :roomId
            """)
    Optional<ChatRoomSummaryDto> findSummaryById(@Param("roomId") Long roomId);

    @Query("""
            SELECT cp.chatRoom FROM ChatParticipant cp
            WHERE cp.user.id = :userId
            AND cp.status = com.loop.loop_backend.Chat.domain.ParticipantStatus.ACTIVE
            """)
    List<ChatRoom> findActiveRoomsByUserId(@Param("userId") Long userId);

    // 두 유저 사이 DIRECT 방. 참여자 상태 무관 — LINE식으로 페어당 방 1개를 rejoin/hide 토글하므로.
    // race로 과거 중복 생긴 경우 대비해 List로 반환, 오래된 것 우선.
    @Query("""
            SELECT cr FROM ChatRoom cr
            WHERE cr.type = com.loop.loop_backend.Chat.domain.ChatRoomType.DIRECT
            AND EXISTS (SELECT 1 FROM ChatParticipant p1 WHERE p1.chatRoom = cr AND p1.user.id = :userId1)
            AND EXISTS (SELECT 1 FROM ChatParticipant p2 WHERE p2.chatRoom = cr AND p2.user.id = :userId2)
            ORDER BY cr.id ASC
            """)
    List<ChatRoom> findDirectRoomsBetweenAnyStatus(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
