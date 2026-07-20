package com.loop.loop_backend.Block.repository;

import com.loop.loop_backend.Block.domain.Block;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    boolean existsByBlockerAndBlocked(User blocker, User blocked);

    Optional<Block> findByBlockerAndBlocked(User blocker, User blocked);

    List<Block> findAllByBlocker(User blocker);

    @Query("""
            SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END
            FROM Block b
            WHERE (b.blocker.id = :userId AND b.blocked.id IN :otherIds)
            OR (b.blocker.id IN :otherIds AND b.blocked.id = :userId)
            """)
    boolean existsBlockBetween(
            @Param("userId") Long userId,
            @Param("otherIds") List<Long> otherIds
    );

    // otherIds 중 userId와 차단 관계(어느 쪽이 차단했든)가 있는 id들만 골라서 반환 (목록 필터링용 배치 조회)
    @Query("""
            SELECT CASE WHEN b.blocker.id = :userId THEN b.blocked.id ELSE b.blocker.id END
            FROM Block b
            WHERE (b.blocker.id = :userId AND b.blocked.id IN :otherIds)
               OR (b.blocker.id IN :otherIds AND b.blocked.id = :userId)
            """)
    List<Long> findBlockedRelatedUserIds(
            @Param("userId") Long userId,
            @Param("otherIds") List<Long> otherIds
    );
}