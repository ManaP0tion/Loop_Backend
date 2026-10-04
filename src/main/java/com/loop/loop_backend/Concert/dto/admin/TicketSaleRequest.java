package com.loop.loop_backend.Concert.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 선예매·일반예매 1건 추가·수정 요청(공용). 수정은 PUT이라 블록 전체를 이 값으로 바꾼다 -
 * opensAt을 null로 보내면 예매 일시가 지워지고, vendors를 빼거나 []로 보내면 예매처가 비워진다.
 */
@Schema(description = "예매 블록(선예매·일반예매 1건) 요청. 수정(PUT)은 블록 전체를 이 값으로 바꾼다")
public record TicketSaleRequest(

        @Schema(description = "예매 일시(한국 시간). 비워도 저장되며, 일시가 없는 건은 사용자 화면에 노출되지 않는다. 수정 시 null이면 지운다",
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