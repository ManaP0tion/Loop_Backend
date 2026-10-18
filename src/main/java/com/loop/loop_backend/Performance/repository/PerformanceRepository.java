package com.loop.loop_backend.Performance.repository;

import com.loop.loop_backend.Performance.domain.Performance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    // 관리자·유저 라인업 목록 공용. 이름·이미지를 바로 쓰므로 아티스트를 함께 가져온다.
    @EntityGraph(attributePaths = "artist")
    List<Performance> findByConcertIdOrderByDisplayOrderAsc(Long concertId);

    // 셋리스트 API: 경로의 공연 소속 출연 정보만. 후보 곡을 아티스트로 찾으므로 함께 가져온다.
    @EntityGraph(attributePaths = {"concert", "artist"})
    Optional<Performance> findByIdAndConcertId(Long id, Long concertId);

    // 단독 공연은 출연 정보가 최대 1건
    Optional<Performance> findFirstByConcertId(Long concertId);

    @Query("select coalesce(max(p.displayOrder), 0) from Performance p where p.concert.id = :concertId")
    int findMaxDisplayOrder(@Param("concertId") Long concertId);

    boolean existsByConcertIdAndArtistIdAndDay(Long concertId, Long artistId, int day);

    // 동행 '보고 싶은 무대' 검증: 고른 항목이 모두 이 공연·DAY 라인업인지
    long countByIdInAndConcertIdAndDay(Collection<Long> ids, Long concertId, int day);

    // 공연 수정(AdminConcertService.update) 중에 불린다 - 수정 중인 Concert가 떨어져 나가지 않게 영속성 컨텍스트를 비우지 않는다.
    // 공연 기간이 줄었을 때: 새 일수를 넘는 DAY 삭제
    @Modifying(flushAutomatically = true)
    @Query("delete from Performance p where p.concert.id = :concertId and p.day > :dayCount")
    int deleteByConcertIdAndDayGreaterThan(@Param("concertId") Long concertId, @Param("dayCount") int dayCount);
}
