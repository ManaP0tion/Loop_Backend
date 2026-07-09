package com.loop.loop_backend.Report.service;

import java.util.List;

public interface ReportService {

    void report(Long userId, Long targetUserId, String reason, boolean blockToo, List<String> imageUrls);
}