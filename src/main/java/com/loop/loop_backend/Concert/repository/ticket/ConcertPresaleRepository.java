package com.loop.loop_backend.Concert.repository.ticket;

import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ConcertPresaleRepository extends JpaRepository<ConcertPresale, Long> {

    List<ConcertPresale> findByConcert_Id(Long concertId);

    // 관리자 목록의 예매 등록 여부: 주어진 공연 중 예매 일시가 입력된 선예매가 있는 공연 id (한 페이지를 한 번에 확인)
    @Query("SELECT DISTINCT p.concert.id FROM ConcertPresale p WHERE p.concert.id IN :concertIds AND p.opensAt IS NOT NULL")
    List<Long> findConcertIdsWithOpensAt(@Param("concertIds") Collection<Long> concertIds);
}