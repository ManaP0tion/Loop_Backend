package com.loop.loop_backend.ConcertImport.repository;

import com.loop.loop_backend.ConcertImport.domain.ConcertImport;
import com.loop.loop_backend.ConcertImport.domain.ImportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConcertImportRepository extends JpaRepository<ConcertImport, Long> {

    // 재수집 멱등성 기준: (kopisId, matchedArtist) 조합으로 기존 import를 찾는다.
    Optional<ConcertImport> findByKopisIdAndMatchedArtistIsNull(String kopisId);
    Optional<ConcertImport> findByKopisIdAndMatchedArtist_Id(String kopisId, Long matchedArtistId);

    Page<ConcertImport> findByStatus(ImportStatus status, Pageable pageable);

    // KOPIS 검색 결과의 등록 상태 계산용: 검색된 공연들의 대기열 행을 한 번에 가져온다.
    List<ConcertImport> findByKopisIdIn(Collection<String> kopisIds);
}
