package com.loop.loop_backend.Song.DTO;

import java.util.List;

/**
 * CSV 업로드 결과.
 * updated = 로마자·한글 반영, unchanged = 두 칸이 비어 기존 값 유지, deleted = CSV에 없어 삭제된 곡 수(deleteMissing일 때만),
 * unmatchedRows = id·trackId로 곡을 못 찾은 행 번호(헤더 제외 1부터)
 */
public record CsvImportResult(int updated, int unchanged, int deleted, List<Long> unmatchedRows) {}
