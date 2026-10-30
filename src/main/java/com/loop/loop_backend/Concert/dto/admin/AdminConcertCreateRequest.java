package com.loop.loop_backend.Concert.dto.admin;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 공연 직접 등록 요청 (KOPIS에 없는 공연). 항상 비공개로 만들어진다 - 포스터는 공연 id가 생긴 뒤 업로드할 수 있어서,
 * 등록 → 포스터 업로드 → 수정(PATCH)에서 공개로 전환하는 순서가 된다. 공개할 때 필수값을 검사한다.
 */
@Schema(description = "공연 직접 등록 요청. 비공개로 생성되고, 공개는 수정 API에서 한다. 필수: category, title")
public record AdminConcertCreateRequest(

        @NotNull
        @Schema(description = "공연 유형 (필수). STAGE(단독 공연) / FESTIVAL(페스티벌)",
                example = "STAGE", requiredMode = Schema.RequiredMode.REQUIRED)
        ConcertCategory category,

        @NotBlank
        @Size(max = 255)
        @Schema(description = "공연명 (필수)", example = "YUURI LIVE [서울]", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "공연명 별칭(검색용). 공백·중복은 정리된다", example = "[\"유우리\"]", types = {"array", "null"})
        List<@Size(max = 255) String> titleAliases,

        @Schema(description = "공연 시작일", example = "2026-12-05", types = {"string", "null"})
        LocalDate startDate,

        @Schema(description = "공연 종료일. 하루 공연이면 시작일과 같다", example = "2026-12-06", types = {"string", "null"})
        LocalDate endDate,

        @Schema(description = "DAY 순서대로의 공연 시각(HH:mm). 개수는 공연 일수와 같아야 하고, 미정인 DAY는 null. 기간이 있어야 보낼 수 있다",
                example = "[\"18:00\", null]", types = {"array", "null"})
        List<LocalTime> showtimes,

        @Schema(description = "공연장 id (공연장 관리)", example = "5", types = {"integer", "null"})
        Long venueId,

        @Size(max = 1)
        @Schema(description = "아티스트 id 목록. 내한 공연만 지정하며 지금은 최대 1명. 페스티벌은 비워 둔다",
                example = "[12]", types = {"array", "null"})
        List<Long> artistIds,

        @Schema(description = "예상 곡 수. 내한 공연만, 1 이상(0이면 400). 아티스트가 없거나 페스티벌이면 400(페스티벌은 라인업에서 헤드라이너별로). 비워 두면 셋리스트에서 20곡으로 처리", example = "20", types = {"integer", "null"})
        Integer expectedSongCount,

        @Schema(description = "숙소 섹션 노출 여부. **공연장(venueId)에 저장된다** - 같은 공연장의 다른 공연에도 반영. " +
                "켜려면 딥링크가 있어야 하고, 공연장 없이 켜면 400. null이면 공연장의 현재 값 유지",
                example = "false", types = {"boolean", "null"})
        Boolean lodgingVisible,

        @Size(max = 1000)
        @Schema(description = "숙소 딥링크(완성된 URL, http/https). **공연장(venueId)에 저장된다**. " +
                "공연장 없이 보내면 400. null이면 공연장의 현재 값 유지, 빈 문자열(\"\")이면 비움",
                example = "https://trip.example/deeplink?trip_sub=PF297519", types = {"string", "null"})
        String lodgingUrl,

        @Size(max = 500)
        @Schema(description = "특설 공식 사이트 URL. http/https 주소만(아니면 400)", example = "https://yuuri-live.example.com",
                types = {"string", "null"})
        String officialSiteUrl,

        @Size(max = 500)
        @Schema(description = "공연 공식 인스타그램 URL(페스티벌 공식 채널용, 유형과 상관없이 저장). http/https 주소만(아니면 400)",
                example = "https://www.instagram.com/summersonic_official", types = {"string", "null"})
        String instagramUrl,

        @Size(max = 500)
        @Schema(description = "공연 공식 X URL(페스티벌 공식 채널용, 유형과 상관없이 저장). http/https 주소만(아니면 400)",
                example = "https://x.com/summer_sonic", types = {"string", "null"})
        String xUrl,

        @Schema(description = "선예매 목록. 블록마다 예매 일시(미정이면 null) + 예매처 목록", types = {"array", "null"})
        List<@Valid TicketSaleRequest> presales,

        @Schema(description = "일반예매 목록. 블록마다 예매 일시(미정이면 null) + 예매처 목록", types = {"array", "null"})
        List<@Valid TicketSaleRequest> generalSales
) {
}