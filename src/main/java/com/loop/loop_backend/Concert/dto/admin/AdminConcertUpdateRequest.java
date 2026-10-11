package com.loop.loop_backend.Concert.dto.admin;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 공연 부분 수정 요청 (PATCH). 다른 PATCH API와 같은 규칙으로 null(또는 필드 없음)은 변경 없음.
 * - 목록(별칭·상품 코드·공연 시각·아티스트·선예매·일반예매)은 보내면 통째로 교체되고, []를 보내면 비운다.
 *   수정 화면이 저장 버튼 하나로 페이지 전체를 저장하므로 예매 정보도 여기서 함께 받는다(한 트랜잭션).
 * - 공개 여부(published)도 여기서 바꾼다. 저장 결과가 공개 상태면 필수값을 검사한다.
 * 포스터는 업로드 API(POST /{id}/image)로만 바꾼다.
 */
@Schema(description = "공연 부분 수정 요청. null이거나 보내지 않은 필드는 변경 없음. " +
        "목록 필드는 보내면 통째로 교체, []는 비움. 저장 결과가 공개 상태면 필수값(유형·공연명·포스터·기간·공연장, 내한이면 아티스트)을 검사한다.")
@Builder
public record AdminConcertUpdateRequest(

        @Schema(description = "공연 유형. J_POP_ARTIST(내한) / JAPAN_FESTIVAL(페스티벌). 페스티벌로 바꾸면 아티스트·예상 곡 수가 비워진다",
                example = "J_POP_ARTIST", types = {"string", "null"})
        ConcertCategory category,

        // null은 통과(변경 없음), 빈 값·공백만 있는 문자열은 거절
        @Pattern(regexp = ".*\\S.*")
        @Size(max = 255)
        @Schema(description = "공연명. 비울 수 없다(빈 값이면 400)", example = "YUURI LIVE [서울]", types = {"string", "null"})
        String title,

        @Schema(description = "공연명 별칭. 보내면 통째로 교체, []는 비움", example = "[\"유우리\"]", types = {"array", "null"})
        List<@Size(max = 255) String> titleAliases,

        @Schema(description = "공연 시작일", example = "2026-12-05", types = {"string", "null"})
        LocalDate startDate,

        @Schema(description = "공연 종료일", example = "2026-12-06", types = {"string", "null"})
        LocalDate endDate,

        @Schema(description = "DAY 순서대로의 공연 시각(HH:mm). 개수는 (바뀐) 공연 일수와 같아야 하고 미정인 DAY는 null. " +
                "기간만 바꾸고 이 값을 보내지 않으면 같은 날짜의 시각은 유지, 기간 밖은 삭제, 새 DAY는 미정",
                example = "[\"18:00\", null]", types = {"array", "null"})
        List<LocalTime> showtimes,

        @Schema(description = "공연장 id", example = "5", types = {"integer", "null"})
        Long venueId,

        @Size(max = 1)
        @Schema(description = "아티스트 id 목록. 보내면 교체, []는 비움. 내한 공연만, 지금은 최대 1명",
                example = "[5]", types = {"array", "null"})
        List<Long> artistIds,

        @Schema(description = "예상 곡 수. 내한 공연만, 1 이상(0이면 400)", example = "20", types = {"integer", "null"})
        Integer expectedSongCount,

        @Schema(description = "숙소 섹션 노출 여부. 켜려면 딥링크가 있어야 한다", example = "true", types = {"boolean", "null"})
        Boolean lodgingVisible,

        @Size(max = 1000)
        @Schema(description = "숙소 딥링크(완성된 URL). 빈 문자열(\"\")이면 비움",
                example = "https://trip.example/deeplink?trip_sub=PF297519", types = {"string", "null"})
        String lodgingUrl,

        @Size(max = 500)
        @Schema(description = "특설 공식 사이트 URL. http/https 주소만(아니면 400). 빈 문자열(\"\")이면 비움",
                example = "https://yuuri-live.example.com", types = {"string", "null"})
        String officialSiteUrl,

        @Schema(description = "관련 상품 코드(CD Japan). 보내면 통째로 교체, []는 비움", example = "[\"PCXP-51237\"]", types = {"array", "null"})
        List<@Size(max = 100) String> productCodes,

        @Schema(description = "선예매 목록. 보내면 통째로 교체, []는 비움, null이면 유지. 블록마다 예매 일시(미정이면 null) + 예매처 목록",
                types = {"array", "null"})
        List<@Valid TicketSaleRequest> presales,

        @Schema(description = "일반예매 목록. 보내면 통째로 교체, []는 비움, null이면 유지. 블록마다 예매 일시(미정이면 null) + 예매처 목록",
                types = {"array", "null"})
        List<@Valid TicketSaleRequest> generalSales,

        @Schema(description = "공개 여부. 저장 결과가 공개면 필수값(유형·공연명·포스터·기간·공연장, 내한이면 아티스트)을 검사하고, " +
                "비공개→공개 전환 시각이 기록된다. 포스터는 POST /{id}/image로 먼저 올려야 한다",
                example = "false", types = {"boolean", "null"})
        Boolean published
) {
}