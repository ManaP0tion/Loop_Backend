package com.loop.loop_backend.Report.event;

import java.util.List;

public record ReportCreatedEvent(
        Long reportId,
        String reporterNickname,
        String targetNickname,
        String reason,
        String detail,
        List<String> imageUrls
) {
}