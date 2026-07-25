package com.loop.loop_backend.HashTag.repository;

import com.loop.loop_backend.HashTag.domain.UserHashtag;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserHashtagRepository extends JpaRepository<UserHashtag, Long> {

    List<UserHashtag> findAllByUser(User user);

    int countByUser(User user);

    boolean existsByUserAndTag(User user, String tag);

    Optional<UserHashtag> findByIdAndUser(Long id, User user);

    void deleteAllByUser(User user);
}
