package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConcertRepository extends JpaRepository<Concert, Long> {

    @Query("SELECT c FROM Concert c " +
            "WHERE c.startDate IS NULL OR c.startDate >= :date " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> findUpcomingOrUndated(@Param("date") LocalDate date);

    @Query("SELECT c FROM Concert c " +
            "WHERE c.category = :category AND (c.startDate IS NULL OR c.startDate >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> findUpcomingOrUndatedByCategory(@Param("category") ConcertCategory category,
                                                  @Param("date") LocalDate date);

    @Query("SELECT c FROM Concert c " +
            "WHERE c.artist.id = :artistId AND (c.startDate IS NULL OR c.startDate >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> findUpcomingOrUndatedByArtistId(@Param("artistId") Long artistId,
                                                  @Param("date") LocalDate date);

    @Query("SELECT c FROM Concert c LEFT JOIN c.artist a " +
            "WHERE (LOWER(c.title) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.name) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.baseName) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.nameKo) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.nameAlias) LIKE LOWER(CONCAT('%', :title, '%'))) " +
            "AND (c.startDate IS NULL OR c.startDate >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> searchUpcomingOrUndatedByTitle(@Param("title") String title,
                                                 @Param("date") LocalDate date);

    List<Concert> findByArtistId(Long artistId);
    Optional<Concert> findByTitle(String title);
    Optional<Concert> findByKopisIdAndArtistId(String kopisId, Long artistId);
    Optional<Concert> findByKopisIdAndArtistIsNull(String kopisId);
}
