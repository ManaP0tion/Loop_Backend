package com.loop.loop_backend.Mail.service;

import com.loop.loop_backend.Mail.dto.ConcertReminderSummary;
import com.loop.loop_backend.Mail.dto.UnreadChatRoomSummary;

import java.util.List;

public interface MailService {

    void sendReportNotification(Long reportId, String reporterNickname, String targetNickname,
                                 String reason, String detail, List<String> imageUrls);

    void sendUnreadChatNotification(String toEmail, String recipientNickname,
                                    List<UnreadChatRoomSummary> rooms);

    void sendVerificationCode(String toEmail, String code);

    void sendConcertReminderNotification(String toEmail, String recipientNickname,
                                         List<ConcertReminderSummary> concerts);
}