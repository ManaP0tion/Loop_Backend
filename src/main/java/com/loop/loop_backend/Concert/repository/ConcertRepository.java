package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConcertRepository extends JpaRepository<Concert, Long> {
    List<Concert> findByArtistId(Long artistId);
    Optional<Concert> findByTitle(String title);
    List<Concert> findByTitleContaining(String title);
}
