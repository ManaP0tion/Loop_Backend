package com.loop.loop_backend.Artist.repository;

import com.loop.loop_backend.Artist.domain.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {
    Optional<Artist> findByName(String name);
    List<Artist> findByAutoFetchConcertsTrue();

    @Query("SELECT a FROM Artist a WHERE " +
            "LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.baseName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.nameKo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.nameAlias) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Artist> searchByAllNames(@Param("q") String q);

    // 이미지 복사 중에 관리자가 imageUrl을 바꿨으면 덮어쓰지 않도록 원래 값일 때만 교체한다. 반환값 0이면 건너뜀.
    @Modifying
    @Transactional
    @Query("UPDATE Artist a SET a.imageUrl = :newUrl WHERE a.id = :id AND a.imageUrl = :oldUrl")
    int replaceImageUrl(@Param("id") Long id, @Param("oldUrl") String oldUrl, @Param("newUrl") String newUrl);
}
