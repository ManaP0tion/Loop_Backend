package com.loop.loop_backend.User.repository;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Role;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserId(String userId);

    boolean existsByUserId(String userId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    boolean existsByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    boolean existsByNickname(String nickname);

    @Query("""
        select u from User u
        where (:status is null or u.status = :status)
          and (:role is null or u.role = :role)
          and (:q is null or lower(u.nickname) like lower(concat('%', :q, '%'))
                          or lower(u.email) like lower(concat('%', :q, '%'))
                          or lower(u.userId) like lower(concat('%', :q, '%')))
        order by u.createdAt desc
        """)
    Page<User> searchForAdmin(@Param("status") Status status,
                              @Param("role") Role role,
                              @Param("q") String q,
                              Pageable pageable);

    List<User> findByRole(Role role);

    List<User> findByStatusAndSuspendedUntilBefore(Status status, LocalDateTime cutoff);
}