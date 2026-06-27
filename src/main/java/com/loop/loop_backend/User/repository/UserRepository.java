package com.loop.loop_backend.User.repository;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserId(String userId);

    boolean existsByUserId(String userId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    boolean existsByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);
}
