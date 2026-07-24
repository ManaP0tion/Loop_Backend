package com.loop.loop_backend.Report.domain;

public enum ReportAction {
    NONE,
    SUSPEND_30D,    // 30일 이용정지
    PERMANENT       // 영구정지 (약관 제12조 2항 사유)
}
