package com.loop.loop_backend.CompanionPost.repository;

import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long>,
        JpaSpecificationExecutor<CompanionPost> {

    boolean existsByUserAndConcertIdAndWatchDay(User user, Long concertId, WatchDay watchDay);

    List<CompanionPost> findAllByUser(User user);

    Optional<CompanionPost> findByUser_IdAndConcertIdAndWatchDay(Long userId, Long concertId, WatchDay watchDay);

    //concert 조회시 해당 콘서트에 등록된 동행 수
    long countByConcertId(Long concertId);
}