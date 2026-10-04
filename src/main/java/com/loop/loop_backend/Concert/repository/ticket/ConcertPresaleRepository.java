package com.loop.loop_backend.Concert.repository.ticket;

import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConcertPresaleRepository extends JpaRepository<ConcertPresale, Long> {

    List<ConcertPresale> findByConcert_Id(Long concertId);

    // 수정·삭제는 경로의 공연에 속한 선예매만 대상으로 한다 (다른 공연의 선예매 id로 접근하는 것을 막음)
    Optional<ConcertPresale> findByIdAndConcert_Id(Long id, Long concertId);
}