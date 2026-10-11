package com.loop.loop_backend.TicketAlarm.repository;

import com.loop.loop_backend.TicketAlarm.domain.TicketAlarm;
import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketAlarmRepository extends JpaRepository<TicketAlarm, Long> {

    boolean existsByUser_IdAndConcert_IdAndType(Long userId, Long concertId, TicketAlarmType type);

    void deleteByUser_IdAndConcert_IdAndType(Long userId, Long concertId, TicketAlarmType type);

    // 공연 상세·토글 응답용: 이 공연에서 내가 켠 알림 종류
    @Query("SELECT a.type FROM TicketAlarm a WHERE a.user.id = :userId AND a.concert.id = :concertId")
    List<TicketAlarmType> findTypesByUserIdAndConcertId(@Param("userId") Long userId, @Param("concertId") Long concertId);

    // 회원탈퇴 정리용(UserServiceImpl.withdrawUser) - 탈퇴는 users 행을 지우지 않아 FK cascade가 동작하지 않는다
    void deleteAllByUser(User user);
}