package com.loop.loop_backend.Song.DTO;

import java.util.List;

/**
 * CSV 업로드 결과.
 * updated = titleKo 반영, unchanged = 한글 칸이 비어 기존 값 유지, unmatchedRows = trackId로 곡을 못 찾은 행 번호(헤더 제외 1부터)
 */
public record CsvImportResult(int updated, int unchanged, List<Long> unmatchedRows) {}
