package com.loop.loop_backend.ConcertImport.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "KOPIS 검색 결과 1건 (KOPIS 목록 값 + 우리 쪽 등록 상태)")
public record KopisSearchItem(

        @Schema(description = "KOPIS 공연 ID. 직접 등록 요청에 그대로 보낸다", example = "PF287093",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String kopisId,

        @Schema(description = "공연명", example = "Vaundy ASIA ARENA TOUR HORO IN SEOUL",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "포스터 URL", types = {"string", "null"})
        String posterUrl,

        @Schema(description = "KOPIS 공연장명", example = "인스파이어 엔터테인먼트 리조트 (아레나)", types = {"string", "null"})
        String venue,

        @Schema(description = "시작일", example = "2026-09-19", types = {"string", "null"})
        LocalDate startDate,

        @Schema(description = "종료일", example = "2026-09-20", types = {"string", "null"})
        LocalDate endDate,

        @Schema(description = "등록 상태. REGISTERED(공연 있음, 등록 불가) / PENDING(검토 대기열에 있음) / " +
                "REJECTED(거절됨, 다시 등록 가능) / NEW(기록 없음)", example = "NEW",
                requiredMode = Schema.RequiredMode.REQUIRED)
        KopisSearchStatus status,

        @Schema(description = "REGISTERED일 때 이 KOPIS 공연으로 만든 공연 id 목록(공연 수정 화면 이동용). 그 외에는 빈 목록",
                example = "[]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Long> concertIds,

        @Schema(description = "추천 아티스트: 공연명에 이름(원어명·한글명·별칭)이 들어 있는 J-POP 아티스트(검토 대기열 자동 매칭과 같은 규칙). " +
                "등록 화면의 아티스트 칸을 미리 채우는 용도이고, 등록 요청의 artistIds에는 관리자가 확인한 1명만 보낸다. 없으면 빈 목록",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<SuggestedArtist> suggestedArtists) {

    @Schema(description = "추천 아티스트")
    public record SuggestedArtist(
            @Schema(description = "아티스트 PK. 등록 요청의 artistIds에 넣는다", example = "3",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            Long id,
            @Schema(description = "아티스트명", example = "Vaundy", requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {
    }
}