package com.loop.loop_backend.Mail.service;

import com.loop.loop_backend.Mail.dto.UnreadChatRoomSummary;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
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

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        Context context = new Context();
        context.setVariable("code", code);

        String html = templateEngine.process("mail/email-verification", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject("[Loop] 이메일 인증 코드");
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("이메일 인증 코드 발송 실패 (to={})", toEmail, e);
            // 사용자가 코드 도착을 기다리는 critical path라, 다른 알림 메일과 달리 실패를 조용히 넘기지 않고 그대로 전파
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}