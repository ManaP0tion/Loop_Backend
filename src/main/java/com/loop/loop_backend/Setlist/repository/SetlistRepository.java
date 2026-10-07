package com.loop.loop_backend.Setlist.repository;

import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SetlistRepository extends JpaRepository<Setlist, Long> {

    Optional<Setlist> findByConcertIdAndType(Long concertId, SetlistType type);

    List<Setlist> findByConcertIdOrderByTypeAsc(Long concertId);
}
