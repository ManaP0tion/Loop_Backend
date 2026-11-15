package com.loop.loop_backend.Concert.domain;

// 예매처 하나(이름 + 예매 링크). Concert의 예매처 목록과 상세 응답 DTO가 공통으로 쓴다.
public record TicketVendorInfo(
        String name, // 예매처명 (예: 인터파크)
        String url   // 예매 링크
) {
}