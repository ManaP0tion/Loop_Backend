package com.loop.loop_backend.CompanionHeart.repository;

import com.loop.loop_backend.CompanionHeart.domain.CompanionHeart;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CompanionHeartRepository extends JpaRepository<CompanionHeart, Long> {

    // 하트 탭 메인화면: 콘서트별로 묶기 위해 전체를 최근 하트순으로 조회
    List<CompanionHeart> findAllByUser_IdOrderByCreatedAtDesc(Long userId);

    // 하트 탭 상세화면: 특정 콘서트에서 내가 하트한 것만 조회 (day별로 묶어서 보여줌)
    List<CompanionHeart> findAllByUser_IdAndCompanionPost_Concert_Id(Long userId, Long concertId);

    boolean existsByUser_IdAndCompanionPost_Id(Long userId, Long companionPostId);

    Optional<CompanionHeart> findByUser_IdAndCompanionPost_Id(Long userId, Long companionPostId);

    void deleteAllByUser(User user);

    void deleteAllByCompanionPost_User(User user);

    // 동행 목록/상세 조회 시 항목별로 존재 여부를 따로 조회하지 않도록, 화면에 보여줄 게시글 id들 중 하트한 것만 한 번에 조회
    @Query("SELECT ch.companionPost.id FROM CompanionHeart ch " +
            "WHERE ch.user.id = :userId AND ch.companionPost.id IN :companionPostIds")
    Set<Long> findHeartedCompanionPostIds(@Param("userId") Long userId,
                                           @Param("companionPostIds") List<Long> companionPostIds);
}