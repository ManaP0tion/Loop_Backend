package com.loop.loop_backend.Artist.repository;

import com.loop.loop_backend.Artist.domain.Artist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArtistRepository extends JpaRepository<Artist, Long> {
    Optional<Artist> findByName(String name);

    @Query("SELECT a FROM Artist a WHERE " +
            "LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.baseName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.nameKo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(a.nameAlias) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Artist> searchByAllNames(@Param("q") String q);
}
