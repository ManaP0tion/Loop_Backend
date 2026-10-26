package com.loop.loop_backend.ConcertImport.dto;

import com.loop.loop_backend.Concert.domain.Concert;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "KOPIS 직접 등록 결과 (검토 대기열 승인 결과와 같은 형태)")
public record ManualImportResponse(

        @Schema(description = "등록에 쓰인 검토 대기열 id. 기존 행(대기·거절)이 있으면 그 행을 다시 쓴다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long importId,

        @Schema(description = "새로 만들어진 공연 id(비공개). 이후 공연 수정·포스터 업로드에 쓴다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long concertId,

        @Schema(description = "공연에 연결된 공연장 id(공연장 관리). KOPIS 시설 조회 실패로 공연장을 정하지 못했으면 null",
                types = {"integer", "null"})
        Long venueId) {

    public static ManualImportResponse of(Long importId, Concert concert) {
        return new ManualImportResponse(importId, concert.getId(),
                concert.getLinkedVenue() != null ? concert.getLinkedVenue().getId() : null);
    }
}