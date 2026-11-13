package com.loop.loop_backend.AiJob.domain;

public enum AiJobStatus {
    PENDING,  // 워커가 가져가길 기다림 (시간 초과로 되돌아온 작업 포함)
    RUNNING,  // 워커가 가져가 처리 중
    DONE,     // 결과 있음 - 결과의 status가 NOT_FOUND여도 DONE(찾아봤는데 없음 = 정상 결과)
    FAILED    // 워커가 실패를 보고했거나, Backend가 시간 초과(LEASE_EXPIRED)로 끝냄
}