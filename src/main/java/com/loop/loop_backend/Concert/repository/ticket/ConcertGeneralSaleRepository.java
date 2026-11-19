package com.loop.loop_backend.Concert.repository.ticket;

import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ConcertGeneralSaleRepository extends JpaRepository<ConcertGeneralSale, Long> {

    List<ConcertGeneralSale> findByConcert_Id(Long concertId);

    // 예매 알림을 켤 수 있는지: 사용자 화면에 보이는(예매 일시가 정해진) 일반예매가 있는지
    boolean existsByConcert_IdAndOpensAtIsNotNull(Long concertId);

    // 관리자 목록의 예매 등록 여부: 주어진 공연 중 예매 일시가 입력된 일반예매가 있는 공연 id (한 페이지를 한 번에 확인)
    @Query("SELECT DISTINCT s.concert.id FROM ConcertGeneralSale s WHERE s.concert.id IN :concertIds AND s.opensAt IS NOT NULL")
    List<Long> findConcertIdsWithOpensAt(@Param("concertIds") Collection<Long> concertIds);
}