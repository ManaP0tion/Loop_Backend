package com.loop.loop_backend.Concert.repository.ticket;

import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConcertGeneralSaleRepository extends JpaRepository<ConcertGeneralSale, Long> {

    List<ConcertGeneralSale> findByConcert_Id(Long concertId);
}