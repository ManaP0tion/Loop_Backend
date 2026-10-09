package com.loop.loop_backend.Mail.service;

import com.loop.loop_backend.Mail.dto.ConcertReminderSummary;
import com.loop.loop_backend.Mail.dto.UnreadChatRoomSummary;

import java.util.List;

public interface MailService {

    void sendReportNotification(Long reportId, String reporterNickname, String targetNickname,
                                 String reason, String detail, List<String> imageUrls);

    void sendInquiryNotification(Long inquiryId, String userNickname, String type, String title, String content, String email);

    void sendUnreadChatNotification(String toEmail, String recipientNickname,
                                    List<UnreadChatRoomSummary> rooms);

    void sendVerificationCode(String toEmail, String code);

    void sendConcertReminderNotification(String toEmail, String recipientNickname,
                                         List<ConcertReminderSummary> concerts);

    void sendNewChatNotification(String toEmail, String recipientNickname, String partnerNickname, String concertTitle);

    void sendSetlistResultNotification(String toEmail, String recipientNickname, Long concertId, String concertTitle);

    // 관리자 콘솔 테스트용: 타입별 샘플 데이터로 지정 주소에 발송
    void sendTest(com.loop.loop_backend.Mail.domain.MailType type, String toEmail);
}