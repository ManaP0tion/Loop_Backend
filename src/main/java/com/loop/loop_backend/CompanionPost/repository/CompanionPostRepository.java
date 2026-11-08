package com.loop.loop_backend.CompanionPost.repository;

import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long>,
        JpaSpecificationExecutor<CompanionPost> {

    boolean existsByUserAndConcert_IdAndWatchDay(User user, Long concertId, WatchDay watchDay);

    // 마이페이지 목록에서 관람일 만료 여부(concert.startDate) 판단에 쓰이므로 함께 로딩
    @EntityGraph(attributePaths = "concert")
    List<CompanionPost> findAllByUser(User user);

    Optional<CompanionPost> findByUser_IdAndConcert_IdAndWatchDay(Long userId, Long concertId, WatchDay watchDay);

    void deleteAllByUser(User user);

    //concert 조회시 해당 콘서트에 등록된 동행 수
    long countByConcert_Id(Long concertId);

    long countByCreatedAtAfter(java.time.LocalDateTime cutoff); // 대시보드: 주간 새 동행글
}