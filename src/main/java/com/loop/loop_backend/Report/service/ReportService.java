package com.loop.loop_backend.Report.service;

public interface ReportService {

    void report(Long userId, Long targetUserId, String reason, boolean blockToo);
}