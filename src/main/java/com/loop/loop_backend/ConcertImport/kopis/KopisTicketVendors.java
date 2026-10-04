package com.loop.loop_backend.ConcertImport.kopis;

import com.loop.loop_backend.Concert.domain.TicketVendorInfo;

import java.util.List;

/**
 * KOPIS 예매처(relatenm + relateurl)를 일반예매 예매처 기본값으로 바꾼다.
 * 놀유니버스만 서비스 표기(NOL)로 바꾸고, 나머지는 KOPIS 이름 그대로 둔다. 관리자는 이 기본값을 자유롭게 고친다.
 */
public final class KopisTicketVendors {

    private static final String NOL_UNIVERSE = "놀유니버스";
    private static final String NOL = "NOL";

    private KopisTicketVendors() {}

    public static List<TicketVendorInfo> toGeneralSaleVendors(List<TicketVendorInfo> kopisVendors) {
        if (kopisVendors == null) return List.of();
        return kopisVendors.stream()
                .map(v -> new TicketVendorInfo(displayName(v.name()), v.url()))
                .toList();
    }

    private static String displayName(String kopisName) {
        if (kopisName == null) return null;
        String name = kopisName.trim();
        return NOL_UNIVERSE.equals(name) ? NOL : name;
    }
}