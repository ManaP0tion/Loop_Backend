package com.loop.loop_backend.Concert.domain.ticket;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;

import java.util.List;

/**
 * 선예매·일반예매 공통 예매처 규칙.
 * - 이름은 필수(화면에 표시할 이름이 없으면 빈 버튼이 된다). 비어 있으면 IllegalArgumentException → 400.
 * - URL은 선택(공식 안내처럼 링크가 없을 수 있다). 빈 값은 null로 둔다.
 * - 앞뒤 공백은 지운다. 예매처가 0개인 건도 허용한다.
 */
final class TicketVendors {

    private TicketVendors() {}

    static List<TicketVendorInfo> normalize(List<TicketVendorInfo> vendors) {
        if (vendors == null) return List.of();
        return vendors.stream()
                .map(TicketVendors::normalize)
                .toList();
    }

    private static TicketVendorInfo normalize(TicketVendorInfo vendor) {
        if (vendor == null || vendor.name() == null || vendor.name().isBlank()) {
            throw new IllegalArgumentException("예매처 이름은 비울 수 없다");
        }
        String url = (vendor.url() == null || vendor.url().isBlank()) ? null : vendor.url().trim();
        return new TicketVendorInfo(vendor.name().trim(), url);
    }
}