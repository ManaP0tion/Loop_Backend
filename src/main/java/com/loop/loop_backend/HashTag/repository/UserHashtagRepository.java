package com.loop.loop_backend.HashTag.repository;

import com.loop.loop_backend.HashTag.domain.UserHashtag;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserHashtagRepository extends JpaRepository<UserHashtag, Long> {

    List<UserHashtag> findAllByUser(User user);

    // 동행 프로필 목록: 여러 작성자의 성격(해시태그)을 쿼리 한 번으로, 저장한 순서대로
    List<UserHashtag> findAllByUser_IdInOrderByIdAsc(Collection<Long> userIds);

    int countByUser(User user);

    boolean existsByUserAndTag(User user, String tag);

    Optional<UserHashtag> findByIdAndUser(Long id, User user);

    void deleteAllByUser(User user);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserHashtag h WHERE h.user = :user")
    void deleteAllInBulkByUser(@Param("user") User user);
}
