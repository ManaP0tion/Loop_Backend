package com.loop.loop_backend.Song.DTO;

/**
 * iTunes 불러오기 요약.
 * skipped = 소프트 삭제된 곡이라 되살리지 않은 수, duplicated = 같은 제목의 다른 버전이라 건너뛴 수
 */
public record SongFetchResult(int created, int updated, int skipped, int duplicated) {}
