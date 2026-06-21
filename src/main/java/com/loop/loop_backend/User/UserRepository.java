package com.loop.loop_backend.User;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    /* userId로 사용자 조회 */
    Optional<User> findByUserId(String userId);

    /* userId 존재 여부 확인 */
    boolean existsByUserId(String userId);

    /* email로 사용자 조회 */
    Optional<User> findByEmail(String email);

    /* email 존재 여부 확인 */
    boolean existsByEmail(String email);

    /* status로 사용자 조회 (여러 명) */
    List<User> findByStatus(UserStatus status);

    /* name으로 사용자 조회 (여러 명) */
    List<User> findByName(String name);
}

