package com.loop.loop_backend.Concert.dto;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 공개 응답의 예매 블록(선예매·일반예매 1건).
 * 예매 일시가 없는 블록은 사용자 화면에 보이지 않는다(명세) - 관리자가 일시를 넣으면 그때부터 나온다.
 * 어드민용 TicketSaleResponse(블록 id, 일시 없는 블록 포함)와 규칙이 달라 따로 둔다.
 */
@Schema(description = "예매 블록. 예매 일시가 정해진 것만 내려간다")
public record ConcertTicketSaleDto(

        @Schema(description = "예매 일시(한국 시간)", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime opensAt,

        @Schema(description = "예매처 목록 (이름 + 링크, 링크는 없을 수 있음)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<TicketVendorInfo> vendors
) {

    public static List<ConcertTicketSaleDto> fromPresales(List<ConcertPresale> presales) {
        return scheduled(presales, ConcertPresale::getOpensAt, ConcertPresale::getId, ConcertPresale::getVendors);
    }

    public static List<ConcertTicketSaleDto> fromGeneralSales(List<ConcertGeneralSale> sales) {
        return scheduled(sales, ConcertGeneralSale::getOpensAt, ConcertGeneralSale::getId, ConcertGeneralSale::getVendors);
    }

    // 일시 있는 블록만, 일시 순(같으면 등록 순)
    private static <T> List<ConcertTicketSaleDto> scheduled(List<T> sales, Function<T, LocalDateTime> opensAt,
                                                            Function<T, Long> id, Function<T, List<TicketVendorInfo>> vendors) {
        return sales.stream()
                .filter(sale -> opensAt.apply(sale) != null)
                .sorted(Comparator.comparing(opensAt).thenComparing(id))
                .map(sale -> new ConcertTicketSaleDto(opensAt.apply(sale),
                        vendors.apply(sale) == null ? List.of() : List.copyOf(vendors.apply(sale))))
                .toList();
    }
}