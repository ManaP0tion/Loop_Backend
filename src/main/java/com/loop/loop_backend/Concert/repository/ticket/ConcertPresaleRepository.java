package com.loop.loop_backend.Concert.repository.ticket;

import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConcertPresaleRepository extends JpaRepository<ConcertPresale, Long> {

    List<ConcertPresale> findByConcert_Id(Long concertId);
}