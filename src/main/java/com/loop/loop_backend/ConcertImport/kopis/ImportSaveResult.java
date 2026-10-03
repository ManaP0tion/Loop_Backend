package com.loop.loop_backend.ConcertImport.kopis;

/**
 * 검토 큐 저장 결과를 행 단위로 센 값. 싱크가 끝났을 때 새로 들어온 건지, 기존 행을 갱신한 건지 로그로 구분하려고 쓴다.
 * - created: 새로 저장 (PENDING)
 * - updated: 검토 전(PENDING) 행을 최신 정보로 갱신
 * - skipped: 이미 승인/반려된 행이라 건드리지 않음
 */
public record ImportSaveResult(int created, int updated, int skipped) {

    public static final ImportSaveResult NONE = new ImportSaveResult(0, 0, 0);
    public static final ImportSaveResult CREATED = new ImportSaveResult(1, 0, 0);
    public static final ImportSaveResult UPDATED = new ImportSaveResult(0, 1, 0);
    public static final ImportSaveResult SKIPPED = new ImportSaveResult(0, 0, 1);

    public ImportSaveResult plus(ImportSaveResult other) {
        return new ImportSaveResult(created + other.created, updated + other.updated, skipped + other.skipped);
    }
}