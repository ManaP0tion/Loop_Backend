package com.loop.loop_backend.CompanionPost.repository;

import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanionPostRepository extends JpaRepository<CompanionPost, Long> {

    boolean existsByUserAndConcertIdAndWatchDay(User user, Long concertId, WatchDay watchDay);

    List<CompanionPost> findAllByUser(User user);
}