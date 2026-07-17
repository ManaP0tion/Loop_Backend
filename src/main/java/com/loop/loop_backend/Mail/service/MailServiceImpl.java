package com.loop.loop_backend.Mail.service;

import com.loop.loop_backend.Mail.dto.UnreadChatRoomSummary;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${report.admin-email}")
    private String adminEmail;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Override
    public void sendReportNotification(Long reportId, String reporterNickname, String targetNickname,
                                        String reason, String detail, List<String> imageUrls) {
        Context context = new Context();
        context.setVariable("reportId", reportId);
        context.setVariable("reporterNickname", reporterNickname);
        context.setVariable("targetNickname", targetNickname);
        context.setVariable("reason", reason);
        context.setVariable("detail", detail);
        context.setVariable("imageUrls", imageUrls == null ? List.of() : imageUrls);

        String html = templateEngine.process("mail/report-notification", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(adminEmail);
            helper.setSubject("[Loop] 신고 접수 (신고 ID: " + reportId + ")");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("신고 알림 메일 발송 실패 (reportId={})", reportId, e);
        }
    }

    @Override
    @Async("mailExecutor")
    public void sendUnreadChatNotification(String toEmail, String recipientNickname,
                                           List<UnreadChatRoomSummary> rooms) {
        if (toEmail == null || toEmail.isBlank() || rooms == null || rooms.isEmpty()) {
            return;
        }

        long totalUnread = rooms.stream().mapToLong(UnreadChatRoomSummary::unreadCount).sum();

        Context context = new Context();
        context.setVariable("recipientNickname", recipientNickname);
        context.setVariable("rooms", rooms);
        context.setVariable("totalUnread", totalUnread);
        context.setVariable("frontendUrl", frontendUrl);

        String html = templateEngine.process("mail/chat-unread-notification", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("[Loop] 확인하지 않은 메시지가 " + totalUnread + "건 있어요");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("미확인 채팅 알림 메일 발송 실패 (to={})", toEmail, e);
        }
    }
}