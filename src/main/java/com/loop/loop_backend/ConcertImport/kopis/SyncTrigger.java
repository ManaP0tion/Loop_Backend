package com.loop.loop_backend.ConcertImport.kopis;

/** KOPIS 싱크를 누가 시작했는지. 완료 알림 같은 후처리가 실행 경로에 따라 다르게 동작할 때 쓴다. */
public enum SyncTrigger {
    /** 매일 09:00 스케줄러 */
    SCHEDULED,
    /** 어드민 수동 실행(POST /api/admin/concerts/sync) */
    MANUAL
}