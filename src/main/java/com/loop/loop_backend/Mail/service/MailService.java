package com.loop.loop_backend.Mail.service;

import java.util.List;

public interface MailService {

    void sendReportNotification(Long reportId, String reporterNickname, String targetNickname,
                                 String reason, List<String> imageUrls);
}