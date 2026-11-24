package com.loop.loop_backend.Mail.service;

import com.loop.loop_backend.Mail.dto.NewConcertSummary;
import com.loop.loop_backend.Mail.dto.TicketAlarmMail;
import com.loop.loop_backend.Mail.dto.TicketAlarmTiming;
import com.loop.loop_backend.Mail.dto.UnreadChatRoomSummary;

import java.util.List;

public interface MailService {

    void sendReportNotification(Long reportId, String reporterNickname, String targetNickname,
                                 String reason, String detail, List<String> imageUrls);

    void sendInquiryNotification(Long inquiryId, String userNickname, String type, String title, String content, String email);

    void sendUnreadChatNotification(String toEmail, String recipientNickname,
                                    List<UnreadChatRoomSummary> rooms);

    void sendVerificationCode(String toEmail, String code);

    void sendNewChatNotification(String toEmail, String recipientNickname, String partnerNickname, String concertTitle);

    void sendSetlistResultNotification(String toEmail, String recipientNickname, Long concertId, String concertTitle);

    void sendNewConcertNotification(String toEmail, String recipientNickname, List<NewConcertSummary> concerts);

    // 예매 일정 하나당 한 통. unsubscribeUrl이 null이면 수신거부 안내를 넣지 않는다
    void sendTicketAlarmNotification(String toEmail, String recipientNickname, TicketAlarmMail mail,
                                     TicketAlarmTiming timing, String unsubscribeUrl);

    // 관리자 콘솔 테스트용: 타입별 샘플 데이터로 지정 주소에 발송
    void sendTest(com.loop.loop_backend.Mail.domain.MailType type, String toEmail);
}