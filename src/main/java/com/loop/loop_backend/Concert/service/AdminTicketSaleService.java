package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleRequest;
import com.loop.loop_backend.Concert.dto.admin.TicketSaleResponse;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 선예매·일반예매 추가·수정·삭제(AD-01 예매 유형). 예매처 규칙(이름 필수 등)은 엔티티가 검사한다.
 * 수정·삭제는 경로의 공연에 속한 건만 대상으로 한다 - 다른 공연의 예매 id로 접근하면 404.
 */
@Service
@RequiredArgsConstructor
public class AdminTicketSaleService {

    private final ConcertRepository concertRepository;
    private final ConcertPresaleRepository presaleRepository;
    private final ConcertGeneralSaleRepository generalSaleRepository;

    // ---------- 선예매 ----------

    @Transactional
    public TicketSaleResponse addPresale(Long concertId, TicketSaleRequest req) {
        ConcertPresale presale = presaleRepository.save(ConcertPresale.builder()
                .concert(findConcert(concertId))
                .opensAt(req.opensAt())
                .vendors(vendorsOf(req))
                .build());
        return TicketSaleResponse.from(presale);
    }

    @Transactional
    public TicketSaleResponse updatePresale(Long concertId, Long presaleId, TicketSaleRequest req) {
        ConcertPresale presale = findPresale(concertId, presaleId);
        presale.update(req.opensAt(), vendorsOf(req));
        return TicketSaleResponse.from(presale);
    }

    @Transactional
    public void deletePresale(Long concertId, Long presaleId) {
        presaleRepository.delete(findPresale(concertId, presaleId));
    }

    // ---------- 일반예매 ----------

    @Transactional
    public TicketSaleResponse addGeneralSale(Long concertId, TicketSaleRequest req) {
        ConcertGeneralSale sale = generalSaleRepository.save(ConcertGeneralSale.builder()
                .concert(findConcert(concertId))
                .opensAt(req.opensAt())
                .vendors(vendorsOf(req))
                .build());
        return TicketSaleResponse.from(sale);
    }

    @Transactional
    public TicketSaleResponse updateGeneralSale(Long concertId, Long saleId, TicketSaleRequest req) {
        ConcertGeneralSale sale = findGeneralSale(concertId, saleId);
        sale.update(req.opensAt(), vendorsOf(req));
        return TicketSaleResponse.from(sale);
    }

    @Transactional
    public void deleteGeneralSale(Long concertId, Long saleId) {
        generalSaleRepository.delete(findGeneralSale(concertId, saleId));
    }

    // ---------- 공통 ----------

    private Concert findConcert(Long concertId) {
        return concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    }

    private ConcertPresale findPresale(Long concertId, Long presaleId) {
        findConcert(concertId); // 공연이 없으면 공연 404, 공연은 있는데 그 공연의 건이 아니면 예매 404
        return presaleRepository.findByIdAndConcert_Id(presaleId, concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_SALE_NOT_FOUND));
    }

    private ConcertGeneralSale findGeneralSale(Long concertId, Long saleId) {
        findConcert(concertId);
        return generalSaleRepository.findByIdAndConcert_Id(saleId, concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_SALE_NOT_FOUND));
    }

    private static List<TicketVendorInfo> vendorsOf(TicketSaleRequest req) {
        if (req.vendors() == null) return List.of();
        return req.vendors().stream()
                .map(v -> new TicketVendorInfo(v.name(), v.url()))
                .toList();
    }
}
