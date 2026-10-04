package com.loop.loop_backend.Concert.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 예매 블록(선예매·일반예매 1건). 공연 등록·수정 요청의 presales / generalSales 목록 원소로 쓴다.
 * 목록은 통째로 교체되므로 블록마다 예매 일시와 예매처를 모두 담아 보낸다(opensAt null = 미정).
 */
@Schema(description = "예매 블록(선예매·일반예매 1건). 예매 일시 + 예매처 목록")
public record TicketSaleRequest(

        @Schema(description = "예매 일시(한국 시간). 미정이면 null. 일시가 없는 블록은 사용자 화면에 노출되지 않는다",
                example = "2026-10-20T20:00", types = {"string", "null"})
        LocalDateTime opensAt,

        @Schema(description = "예매처 목록. 0개도 허용", types = {"array", "null"})
        List<@Valid Vendor> vendors
) {

    @Schema(description = "예매처 하나")
    public record Vendor(
            @NotBlank
            @Size(max = 100)
            @Schema(description = "예매처 이름 (필수)", example = "NOL", requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Size(max = 1000)
            @Schema(description = "예매 링크. 없으면 null(링크 버튼 미노출)", example = "https://nol.example/goods/1", types = {"string", "null"})
            String url) {
    }
}