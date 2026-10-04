package com.loop.loop_backend.Concert.repository.ticket;

import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConcertGeneralSaleRepository extends JpaRepository<ConcertGeneralSale, Long> {

    List<ConcertGeneralSale> findByConcert_Id(Long concertId);

    // 수정·삭제는 경로의 공연에 속한 일반예매만 대상으로 한다 (다른 공연의 일반예매 id로 접근하는 것을 막음)
    Optional<ConcertGeneralSale> findByIdAndConcert_Id(Long id, Long concertId);
}