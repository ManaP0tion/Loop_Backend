package com.loop.loop_backend.TicketAlarm.dto;

import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/** 한 공연에 대한 내 예매 알림 상태. 켜기·끄기 뒤 토글 두 개를 다시 그릴 수 있게 둘 다 돌려준다. */
@Schema(description = "내 예매 알림 상태")
public record TicketAlarmResponse(

        @Schema(description = "선예매 알림 켜짐 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean presale,

        @Schema(description = "일반예매 알림 켜짐 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean generalSale
) {

    public static TicketAlarmResponse of(Set<TicketAlarmType> types) {
        return new TicketAlarmResponse(types.contains(TicketAlarmType.PRESALE),
                types.contains(TicketAlarmType.GENERAL_SALE));
    }
}