package com.loop.loop_backend.ConcertImport.kopis;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// KOPIS 예매처를 일반예매 기본값으로 불러오는 규칙(PM 확정):
// 놀유니버스는 NOL로 바꾸고, 나머지는 KOPIS 이름 그대로 불러온다. URL은 그대로 둔다.
class KopisTicketVendorsTest {

    @Test
    void 놀유니버스는_NOL로_바꾸고_나머지는_이름_그대로_불러온다() {
        List<TicketVendorInfo> kopis = List.of(
                new TicketVendorInfo("놀유니버스", "https://nol"),
                new TicketVendorInfo("멜론티켓", "https://melon"),
                new TicketVendorInfo("예스24", "https://yes24"),
                new TicketVendorInfo("티켓링크", "https://link"));

        assertThat(KopisTicketVendors.toGeneralSaleVendors(kopis)).containsExactly(
                new TicketVendorInfo("NOL", "https://nol"),
                new TicketVendorInfo("멜론티켓", "https://melon"),
                new TicketVendorInfo("예스24", "https://yes24"),
                new TicketVendorInfo("티켓링크", "https://link"));
    }

    @Test
    void 이름의_앞뒤_공백은_지운다() {
        assertThat(KopisTicketVendors.toGeneralSaleVendors(List.of(new TicketVendorInfo(" 놀유니버스 ", "https://nol"))))
                .containsExactly(new TicketVendorInfo("NOL", "https://nol"));
    }

    @Test
    void KOPIS_예매처가_없으면_빈_목록이다() {
        assertThat(KopisTicketVendors.toGeneralSaleVendors(null)).isEmpty();
        assertThat(KopisTicketVendors.toGeneralSaleVendors(List.of())).isEmpty();
    }
}