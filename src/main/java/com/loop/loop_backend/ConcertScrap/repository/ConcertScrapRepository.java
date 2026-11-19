package com.loop.loop_backend.ConcertScrap.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.ConcertScrap.domain.ConcertScrap;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConcertScrapRepository extends JpaRepository<ConcertScrap, Long> {

    boolean existsByUser_IdAndConcert_Id(Long userId, Long concertId);

    Optional<ConcertScrap> findByUser_IdAndConcert_Id(Long userId, Long concertId);

    void deleteAllByUser(User user);

    // 예정/지난 판단과 정렬은 ConcertRepository의 section 조회(findUpcomingOrUndatedByCategories /
    // findPastByCategories)와 동일해야 한다 - 공연 탭과 스크랩 탭의 구분이 어긋나지 않도록.

    // 예정 공연(날짜 미정 포함) - 임박순, 날짜 미정은 맨 뒤.
    @Query("SELECT c FROM ConcertScrap s JOIN s.concert c LEFT JOIN FETCH c.artist LEFT JOIN FETCH c.linkedVenue " +
            "WHERE s.user.id = :userId " +
            "AND (COALESCE(c.endDate, c.startDate) IS NULL OR COALESCE(c.endDate, c.startDate) >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> findUpcomingOrUndatedScrappedConcerts(@Param("userId") Long userId,
                                                        @Param("date") LocalDate date);

    // 지난 공연 - 최근 종료순. 날짜 미정 공연은 "지난 공연"이 아니므로 제외.
    @Query("SELECT c FROM ConcertScrap s JOIN s.concert c LEFT JOIN FETCH c.artist LEFT JOIN FETCH c.linkedVenue " +
            "WHERE s.user.id = :userId " +
            "AND COALESCE(c.endDate, c.startDate) IS NOT NULL " +
            "AND COALESCE(c.endDate, c.startDate) < :date " +
            "ORDER BY COALESCE(c.endDate, c.startDate) DESC")
    List<Concert> findPastScrappedConcerts(@Param("userId") Long userId,
                                           @Param("date") LocalDate date);
}