package com.loop.loop_backend.Report.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReportService {

    void report(Long userId, Long targetUserId, String reason, String detail, boolean blockToo, List<MultipartFile> images);
}