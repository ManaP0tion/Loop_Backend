package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.ConcertImport;
import com.loop.loop_backend.Concert.domain.ImportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConcertImportRepository extends JpaRepository<ConcertImport, Long> {

    // 재수집 멱등성 기준: (kopisId, matchedArtist) 조합으로 기존 import를 찾는다.
    Optional<ConcertImport> findByKopisIdAndMatchedArtistIsNull(String kopisId);
    Optional<ConcertImport> findByKopisIdAndMatchedArtist_Id(String kopisId, Long matchedArtistId);

    Page<ConcertImport> findByStatus(ImportStatus status, Pageable pageable);
}
