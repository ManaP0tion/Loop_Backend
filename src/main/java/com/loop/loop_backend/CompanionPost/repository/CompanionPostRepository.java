package com.loop.loop_backend.CompanionPost.repository;

import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.ConcertReminderRow;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long>,
        JpaSpecificationExecutor<CompanionPost> {

    boolean existsByUserAndConcertIdAndWatchDay(User user, Long concertId, WatchDay watchDay);

    List<CompanionPost> findAllByUser(User user);

    Optional<CompanionPost> findByUser_IdAndConcertIdAndWatchDay(Long userId, Long concertId, WatchDay watchDay);

    //concert 조회시 해당 콘서트에 등록된 동행 수
    long countByConcertId(Long concertId);

    @Query("""
            SELECT new com.loop.loop_backend.CompanionPost.dto.ConcertReminderRow(
                cp.user.id,
                cp.user.email,
                cp.user.nickname,
                c.id,
                c.title,
                c.venue,
                c.startDate,
                cp.watchDay
            )
            FROM CompanionPost cp, com.loop.loop_backend.Concert.domain.Concert c
            WHERE cp.concertId = c.id
              AND cp.user.status = com.loop.loop_backend.User.domain.Status.ACTIVE
              AND cp.user.email IS NOT NULL
              AND cp.user.concertReminderEmail = true
              AND (
                (cp.watchDay = com.loop.loop_backend.CompanionPost.domain.WatchDay.DAY1 AND c.startDate = :d1) OR
                (cp.watchDay = com.loop.loop_backend.CompanionPost.domain.WatchDay.DAY2 AND c.startDate = :d2) OR
                (cp.watchDay = com.loop.loop_backend.CompanionPost.domain.WatchDay.DAY3 AND c.startDate = :d3) OR
                (cp.watchDay = com.loop.loop_backend.CompanionPost.domain.WatchDay.DAY4 AND c.startDate = :d4)
              )
            ORDER BY cp.user.id, c.startDate
            """)
    List<ConcertReminderRow> findConcertReminderRows(@Param("d1") LocalDate d1,
                                                     @Param("d2") LocalDate d2,
                                                     @Param("d3") LocalDate d3,
                                                     @Param("d4") LocalDate d4);
}