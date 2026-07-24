package com.loop.loop_backend.Report.domain;

public enum ReportStatus {
    RECEIVED,       // 접수
    PROCESSING,     // 처리중
    ACTION_TAKEN,   // 조치완료
    CLOSED          // 종결 (자료 미제출 등)
}
