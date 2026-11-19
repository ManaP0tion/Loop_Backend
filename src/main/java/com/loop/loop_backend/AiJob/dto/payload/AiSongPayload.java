package com.loop.loop_backend.AiJob.dto.payload;

/**
 * 셋리스트 payload 안의 곡 하나. Loop Song의 같은 이름 컬럼 그대로.
 * AI는 찾은 셋리스트 곡을 이 목록과 맞춰 songId(= id)를 돌려준다.
 */
public record AiSongPayload(
        Long id,
        String titleOriginal,
        String titleRomanized,
        String titleKo
) {
}
