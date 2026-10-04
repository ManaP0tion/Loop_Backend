package com.loop.loop_backend.Concert.dto.admin;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** 공연 관리 > 등록된 공연 목록의 한 행. 수정 화면은 id로 상세(GET /api/admin/concerts/{id})를 연다. */
@Schema(description = "등록된 공연 목록의 한 행")
public record AdminConcertRow(

        @Schema(description = "공연 id (수정 화면에서 상세 조회에 쓴다)", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "공연 유형. J_POP_ARTIST(내한) / JAPAN_FESTIVAL(페스티벌). V2 이전 공연은 국내 유형일 수 있다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        ConcertCategory category,

        @Schema(description = "공연명", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "장소 - 공연장 관리에 연결된 공연장 이름. 연결이 없으면 null", types = {"string", "null"})
        String venueName,

        @Schema(description = "공연 시작일", types = {"string", "null"})
        LocalDate startDate,

        @Schema(description = "공연 종료일 (하루 공연은 시작일과 같다)", types = {"string", "null"})
        LocalDate endDate,

        @Schema(description = "공개 여부. false면 사용자에게 '오픈 예정'으로 보인다", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean published,

        @Schema(description = "예매 등록 여부 - 선예매·일반예매 중 예매 일시가 입력된 블록이 하나라도 있으면 true. " +
                "일시가 없는 블록은 사용자 화면에 보이지 않으므로, 승인 직후(KOPIS 예매처만 채워진 상태)는 false다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean ticketScheduled
) {

    public static AdminConcertRow from(Concert c, boolean ticketScheduled) {
        return new AdminConcertRow(c.getId(), c.getCategory(), c.getTitle(),
                c.getLinkedVenue() == null ? null : c.getLinkedVenue().getName(),
                c.getStartDate(), c.getEndDate(), c.isPublished(), ticketScheduled);
    }
}