package com.loop.loop_backend.Concert.dto.admin;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** 예매 블록(선예매·일반예매 1건) 응답(공용). */
@Schema(description = "예매 블록(선예매·일반예매 1건)")
public record TicketSaleResponse(

        @Schema(description = "예매 블록 id (수정·삭제 경로에 쓴다)", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "예매 일시(한국 시간). 미정이면 null", types = {"string", "null"})
        LocalDateTime opensAt,

        @Schema(description = "예매처 목록 (이름 + 링크)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<TicketVendorInfo> vendors
) {

    /** 화면 정렬: 예매 일시 순, 일시가 없는 건은 뒤로, 같으면 등록 순. */
    static final Comparator<TicketSaleResponse> DISPLAY_ORDER = Comparator
            .comparing(TicketSaleResponse::opensAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TicketSaleResponse::id);

    public static TicketSaleResponse from(ConcertPresale presale) {
        return new TicketSaleResponse(presale.getId(), presale.getOpensAt(), vendorsOf(presale.getVendors()));
    }

    public static TicketSaleResponse from(ConcertGeneralSale sale) {
        return new TicketSaleResponse(sale.getId(), sale.getOpensAt(), vendorsOf(sale.getVendors()));
    }

    // 이 컬럼이 생기기 전 행처럼 저장값이 비어 있으면 변환기가 null을 돌려줄 수 있다
    private static List<TicketVendorInfo> vendorsOf(List<TicketVendorInfo> vendors) {
        return vendors == null ? List.of() : List.copyOf(vendors);
    }

    public static List<TicketSaleResponse> fromPresales(List<ConcertPresale> presales) {
        return presales.stream().map(TicketSaleResponse::from).sorted(DISPLAY_ORDER).toList();
    }

    public static List<TicketSaleResponse> fromGeneralSales(List<ConcertGeneralSale> sales) {
        return sales.stream().map(TicketSaleResponse::from).sorted(DISPLAY_ORDER).toList();
    }
}