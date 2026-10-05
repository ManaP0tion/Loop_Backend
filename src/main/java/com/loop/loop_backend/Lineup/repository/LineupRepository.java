package com.loop.loop_backend.Lineup.repository;

import com.loop.loop_backend.Lineup.domain.Lineup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LineupRepository extends JpaRepository<Lineup, Long> {

    // 관리자·유저 목록 공용. 이름·이미지를 바로 쓰므로 아티스트를 함께 가져온다.
    @EntityGraph(attributePaths = "artist")
    List<Lineup> findByConcertIdOrderByDisplayOrderAsc(Long concertId);

    @Query("select coalesce(max(l.displayOrder), 0) from Lineup l where l.concert.id = :concertId")
    int findMaxDisplayOrder(@Param("concertId") Long concertId);

    boolean existsByConcertIdAndArtistIdAndDay(Long concertId, Long artistId, int day);

    // 동행 '보고 싶은 무대' 검증: 고른 항목이 모두 이 공연·DAY 라인업인지
    long countByIdInAndConcertIdAndDay(Collection<Long> ids, Long concertId, int day);

    // 공연 수정(AdminConcertService.update) 중에 불린다 - 수정 중인 Concert가 떨어져 나가지 않게 영속성 컨텍스트를 비우지 않는다.
    // 공연 기간이 줄었을 때: 새 일수를 넘는 DAY 삭제
    @Modifying(flushAutomatically = true)
    @Query("delete from Lineup l where l.concert.id = :concertId and l.day > :dayCount")
    int deleteByConcertIdAndDayGreaterThan(@Param("concertId") Long concertId, @Param("dayCount") int dayCount);

    // 페스티벌이 아닌 유형으로 바뀌었을 때: 전부 삭제
    @Modifying(flushAutomatically = true)
    @Query("delete from Lineup l where l.concert.id = :concertId")
    int deleteByConcertId(@Param("concertId") Long concertId);
}
