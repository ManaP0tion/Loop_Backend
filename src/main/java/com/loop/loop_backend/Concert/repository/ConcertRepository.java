package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConcertRepository extends JpaRepository<Concert, Long> {
    List<Concert> findByStartDateGreaterThanEqualOrderByStartDateAsc(LocalDate date);
    List<Concert> findByArtistId(Long artistId);
    List<Concert> findByArtistIdAndStartDateGreaterThanEqualOrderByStartDateAsc(Long artistId, LocalDate date);
    List<Concert> findByTitleContainingAndStartDateGreaterThanEqualOrderByStartDateAsc(String title, LocalDate date);
    Optional<Concert> findByTitle(String title);
    Optional<Concert> findByKopisIdAndArtistId(String kopisId, Long artistId);
}
