package com.loop.loop_backend.AiJob.dto.payload;

/**
 * payload 안의 아티스트 한 명. Loop Artist의 같은 이름 컬럼 그대로.
 * 단독 공연은 nameKo로 "○○ 내한 예매"를 검색하므로 검색 품질에 가장 중요하다.
 */
public record AiArtistPayload(
        String name,      // 필수
        String baseName,
        String nameKo,
        String nameAlias
) {
}