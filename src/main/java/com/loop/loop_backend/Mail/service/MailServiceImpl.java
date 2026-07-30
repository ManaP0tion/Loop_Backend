package com.loop.loop_backend.Mail.service;

import com.loop.loop_backend.Mail.dto.ConcertReminderSummary;
import com.loop.loop_backend.Mail.dto.UnreadChatRoomSummary;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
public class MailServiceImpl implements MailService {

    private final JavaMailSender gmailMailSender;
    private final JavaMailSender sesMailSender;
    private final SpringTemplateEngine templateEngine;

    // Lombok @RequiredArgsConstructor는 필드의 @Qualifier를 생성자 파라미터로 복사하지 않아서
    // (Spring은 파라미터 애노테이션만 봄) 항상 @Primary(gmailMailSender)로 몰리는 버그가 있었다.
    // 그래서 생성자를 직접 작성해 파라미터에 @Qualifier를 명시한다.
    public MailServiceImpl(
            @Qualifier("gmailMailSender") JavaMailSender gmailMailSender,
            @Qualifier("sesMailSender") JavaMailSender sesMailSender,
            SpringTemplateEngine templateEngine) {
        this.gmailMailSender = gmailMailSender;
        this.sesMailSender = sesMailSender;
        this.templateEngine = templateEngine;
    }

    @Value("${report.admin-email}")
    private String adminEmail;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${ses.mail.from}")
    private String sesFromAddress;

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
            MimeMessage message = gmailMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(adminEmail);
            helper.setSubject("[Loop] 신고 접수 (신고 ID: " + reportId + ")");
            helper.setText(html, true);
            gmailMailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("신고 알림 메일 발송 실패 (reportId={})", reportId, e);
        }
    }

    @Override
    public void sendInquiryNotification(Long inquiryId, String userNickname, String type, String title, String content) {
        Context context = new Context();
        context.setVariable("inquiryId", inquiryId);
        context.setVariable("userNickname", userNickname);
        context.setVariable("type", type);
        context.setVariable("title", title);
        context.setVariable("content", content);

        String html = templateEngine.process("mail/inquiry-notification", context);

        try {
            MimeMessage message = gmailMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(adminEmail);
            helper.setSubject("[Loop] 문의 접수 (문의 ID: " + inquiryId + ")");
            helper.setText(html, true);
            gmailMailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("문의 알림 메일 발송 실패 (inquiryId={})", inquiryId, e);
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
            MimeMessage message = sesMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(sesFromAddress);
            helper.setTo(toEmail);
            helper.setSubject("[Loop] 확인하지 않은 메시지가 " + totalUnread + "건 있어요");
            helper.setText(html, true);
            sesMailSender.send(message);
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
            MimeMessage message = sesMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(sesFromAddress);
            helper.setTo(toEmail);
            helper.setSubject("[Loop] 이메일 인증 코드");
            helper.setText(html, true);
            sesMailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("이메일 인증 코드 발송 실패 (to={})", toEmail, e);
            // 사용자가 코드 도착을 기다리는 critical path라, 다른 알림 메일과 달리 실패를 조용히 넘기지 않고 그대로 전파
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    @Override
    @Async("mailExecutor")
    public void sendConcertReminderNotification(String toEmail, String recipientNickname,
                                                List<ConcertReminderSummary> concerts) {
        if (toEmail == null || toEmail.isBlank() || concerts == null || concerts.isEmpty()) {
            return;
        }

        Context context = new Context();
        context.setVariable("recipientNickname", recipientNickname);
        context.setVariable("concerts", concerts);
        context.setVariable("frontendUrl", frontendUrl);

        String html = templateEngine.process("mail/concert-reminder-notification", context);

        try {
            MimeMessage message = sesMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(sesFromAddress);
            helper.setTo(toEmail);
            helper.setSubject("[Loop] 내일 관람 예정 공연이 " + concerts.size() + "건 있어요");
            helper.setText(html, true);
            sesMailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("공연 하루전 리마인더 메일 발송 실패 (to={})", toEmail, e);
        }
    }

    @Override
    @Async("mailExecutor")
    public void sendNewChatNotification(String toEmail, String recipientNickname, String partnerNickname, String concertTitle) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }

        Context context = new Context();
        context.setVariable("recipientNickname", recipientNickname);
        context.setVariable("partnerNickname", partnerNickname);
        context.setVariable("concertTitle", concertTitle);
        context.setVariable("frontendUrl", frontendUrl);

        String html = templateEngine.process("mail/chat-new-notification", context);

        try {
            MimeMessage message = sesMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(sesFromAddress);
            helper.setTo(toEmail);
            helper.setSubject("[Loop] 새로운 채팅이 시작됐어요");
            helper.setText(html, true);
            sesMailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.error("신규 채팅 알림 메일 발송 실패 (to={})", toEmail, e);
        }
    }
}