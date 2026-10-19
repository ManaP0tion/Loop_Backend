package com.loop.loop_backend.Setlist.repository;

/**
 * 셋리스트·결과 화면에 쓰는 곡 표기. 삭제된 곡도 담는다(deleted=true) - 저장된 셋리스트는 곡이 나중에 삭제돼도 그대로 보여야 해서.
 * Song 엔티티는 @SQLRestriction으로 삭제 곡을 늘 빼므로, 이 값은 네이티브 쿼리로 읽는다.
 */
public record SetlistSong(Long songId, String titleOriginal, String titleKo, String albumArtUrl, boolean deleted) {

    /** 네이티브 쿼리 한 행에서 offset부터 (id, 원제, 한글, 앨범아트, 삭제 여부 0/1) 순서로 읽는다. */
    static SetlistSong fromRow(Object[] row, int offset) {
        return new SetlistSong(((Number) row[offset]).longValue(), (String) row[offset + 1], (String) row[offset + 2],
                (String) row[offset + 3], ((Number) row[offset + 4]).intValue() != 0);
    }
}
