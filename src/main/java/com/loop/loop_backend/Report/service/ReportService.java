package com.loop.loop_backend.Report.service;

import java.util.List;

public interface ReportService {

    void report(Long userId, Long targetUserId, String reason, String detail, boolean blockToo, List<String> imageUrls);
}