package com.loop.loop_backend.Performance.repository;

import com.loop.loop_backend.Performance.domain.Performance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    // 관리자·유저 라인업 목록 공용. 이름·이미지·출연 DAY를 바로 쓰므로 함께 가져온다.
    @EntityGraph(attributePaths = {"artist", "days"})
    List<Performance> findByConcertIdOrderByDisplayOrderAsc(Long concertId);

    // 라인업 추가: 같은 아티스트가 이미 있으면 출연 DAY만 늘린다
    Optional<Performance> findByConcertIdAndArtistId(Long concertId, Long artistId);

    // 공연 수정 시 출연 정보 정리(PerformanceSync). 공연 하나의 출연진이라 많지 않다.
    @EntityGraph(attributePaths = "days")
    List<Performance> findByConcertId(Long concertId);

    // 셋리스트 API: 경로의 공연 소속 출연 정보만. 후보 곡을 아티스트로 찾으므로 함께 가져온다.
    @EntityGraph(attributePaths = {"concert", "artist"})
    Optional<Performance> findByIdAndConcertId(Long id, Long concertId);

    // 단독 공연은 출연 정보가 최대 1건
    Optional<Performance> findFirstByConcertId(Long concertId);

    @Query("select coalesce(max(p.displayOrder), 0) from Performance p where p.concert.id = :concertId")
    int findMaxDisplayOrder(@Param("concertId") Long concertId);

    // 동행 '보고 싶은 무대' 검증: 고른 출연 정보가 모두 이 공연 소속이고 그 DAY에 출연하는지
    @Query("select count(p) from Performance p where p.id in :ids and p.concert.id = :concertId and :day member of p.days")
    long countByIdInAndConcertIdAndDay(@Param("ids") Collection<Long> ids, @Param("concertId") Long concertId,
                                       @Param("day") int day);

    // 실제 셋리스트 등록 리마인드: endDate에 끝난 공개 공연의 셋리스트 대상(단독 공연은 항상 헤드라이너, 페스티벌은 헤드라이너만) 중
    // 실제 셋리스트가 아직 없는 출연 정보. 공연명·아티스트명을 바로 쓰므로 함께 가져온다.
    @Query("""
            select p from Performance p join fetch p.concert c join fetch p.artist
            where c.endDate = :endDate and c.published = true and p.headliner = true
              and not exists (select 1 from Setlist s where s.performance = p
                              and s.type = com.loop.loop_backend.Setlist.domain.SetlistType.ACTUAL)
            order by c.id, p.displayOrder
            """)
    List<Performance> findMissingActualSetlist(@Param("endDate") LocalDate endDate);
}
