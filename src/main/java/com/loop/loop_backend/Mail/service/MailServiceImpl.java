package com.loop.loop_backend.Mail.service;

import com.loop.loop_backend.Mail.domain.MailLog;
import com.loop.loop_backend.Mail.domain.MailType;
import com.loop.loop_backend.Mail.dto.ConcertReminderSummary;
import com.loop.loop_backend.Mail.dto.UnreadChatRoomSummary;
import com.loop.loop_backend.Mail.repository.MailLogRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class MailServiceImpl implements MailService {

    private final JavaMailSender gmailMailSender;
    private final JavaMailSender sesMailSender;
    private final SpringTemplateEngine templateEngine;
    private final MailLogRepository mailLogRepository;

    // Lombok @RequiredArgsConstructor는 필드의 @Qualifier를 생성자 파라미터로 복사하지 않아서
    // (Spring은 파라미터 애노테이션만 봄) 항상 @Primary(gmailMailSender)로 몰리는 버그가 있었다.
    // 그래서 생성자를 직접 작성해 파라미터에 @Qualifier를 명시한다.
    public MailServiceImpl(
            @Qualifier("gmailMailSender") JavaMailSender gmailMailSender,
            @Qualifier("sesMailSender") JavaMailSender sesMailSender,
            SpringTemplateEngine templateEngine,
            MailLogRepository mailLogRepository) {
        this.gmailMailSender = gmailMailSender;
        this.sesMailSender = sesMailSender;
        this.templateEngine = templateEngine;
        this.mailLogRepository = mailLogRepository;
    }

    @Value("${report.admin-email}")
    private String adminEmail;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Value("${ses.mail.from}")
    private String sesFromAddress;

    /**
     * 모든 메일 발송이 지나는 단일 지점. 여기서 발송하고 성공/실패를 MailLog(메타데이터)로 남긴다.
     * SES 발송기는 미검증 발신자를 거부하므로 검증된 주소로 setFrom 한다(gmail은 계정 기본 발신자 사용).
     */
    private void dispatch(JavaMailSender sender, MailType type, String to, String subject, String html) {
        String failReason = null;
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            if (sender == sesMailSender) helper.setFrom(sesFromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
        } catch (MessagingException | MailException e) {
            failReason = e.getMessage();
            log.error("메일 발송 실패 (type={}, to={})", type, to, e);
        }

        // 로그 저장 실패가 실제 발송 결과를 뒤엎지 않도록 격리
        try {
            mailLogRepository.save(MailLog.builder()
                    .type(type).toEmail(to).subject(subject)
                    .success(failReason == null).failReason(failReason).build());
        } catch (Exception logEx) {
            log.error("메일 로그 저장 실패 (type={}, to={})", type, to, logEx);
        }

        // 인증코드는 사용자가 도착을 기다리는 critical path라 실패를 전파(그 외 알림은 조용히 스킵)
        if (failReason != null && type == MailType.VERIFICATION) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

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
        dispatch(gmailMailSender, MailType.REPORT, adminEmail,
                "[Loop] 신고 접수 (신고 ID: " + reportId + ")", html);
    }

    @Override
    public void sendInquiryNotification(Long inquiryId, String userNickname, String type, String title, String content, String email) {
        Context context = new Context();
        context.setVariable("inquiryId", inquiryId);
        context.setVariable("userNickname", userNickname);
        context.setVariable("type", type);
        context.setVariable("title", title);
        context.setVariable("content", content);
        context.setVariable("email", email);

        String html = templateEngine.process("mail/inquiry-notification", context);
        dispatch(gmailMailSender, MailType.INQUIRY, adminEmail,
                "[Loop] 문의 접수 (문의 ID: " + inquiryId + ")", html);
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
        dispatch(sesMailSender, MailType.UNREAD_CHAT, toEmail,
                "[Loop] 확인하지 않은 메시지가 " + totalUnread + "건 있어요", html);
    }

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        Context context = new Context();
        context.setVariable("code", code);

        String html = templateEngine.process("mail/email-verification", context);
        dispatch(sesMailSender, MailType.VERIFICATION, toEmail,
                "[Loop] 이메일 인증 코드", html);
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
        dispatch(sesMailSender, MailType.CONCERT_REMINDER, toEmail,
                "[Loop] 내일 관람 예정 공연이 " + concerts.size() + "건 있어요", html);
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
        dispatch(sesMailSender, MailType.NEW_CHAT, toEmail,
                "[Loop] 새로운 채팅이 시작됐어요", html);
    }

    // 동기 발송 - 호출하는 SetlistResultMailListener가 이미 mailExecutor에서 돈다
    // 적중률 수치는 넣지 않는다(NO.69) - 공연 후 페이지에서 확인하게
    @Override
    public void sendSetlistResultNotification(String toEmail, String recipientNickname, Long concertId, String concertTitle) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }

        Context context = new Context();
        context.setVariable("recipientNickname", recipientNickname);
        context.setVariable("concertId", concertId);
        context.setVariable("concertTitle", concertTitle);
        context.setVariable("frontendUrl", frontendUrl);

        String html = templateEngine.process("mail/setlist-result-notification", context);
        dispatch(sesMailSender, MailType.SETLIST_RESULT, toEmail,
                "[Loop] " + concertTitle + " 셋리스트가 공개됐어요", html);
    }

    @Override
    public void sendTest(MailType type, String toEmail) {
        switch (type) {
            case REPORT -> {
                Context c = new Context();
                c.setVariable("reportId", 0L);
                c.setVariable("reporterNickname", "테스트신고자");
                c.setVariable("targetNickname", "테스트대상");
                c.setVariable("reason", "테스트 신고 사유");
                c.setVariable("detail", "테스트 발송용 상세 내용입니다.");
                c.setVariable("imageUrls", List.of());
                dispatch(gmailMailSender, MailType.REPORT, toEmail,
                        "[Loop][TEST] 신고 접수", templateEngine.process("mail/report-notification", c));
            }
            case INQUIRY -> {
                Context c = new Context();
                c.setVariable("inquiryId", 0L);
                c.setVariable("userNickname", "테스트유저");
                c.setVariable("type", "APP_USAGE");
                c.setVariable("title", "테스트 문의 제목");
                c.setVariable("content", "테스트 발송용 문의 내용입니다.");
                dispatch(gmailMailSender, MailType.INQUIRY, toEmail,
                        "[Loop][TEST] 문의 접수", templateEngine.process("mail/inquiry-notification", c));
            }
            // @Async 메서드지만 self-invocation이라 동기 실행 → 테스트 결과가 바로 잡힌다
            case UNREAD_CHAT -> sendUnreadChatNotification(toEmail, "테스터",
                    List.of(new UnreadChatRoomSummary(0L, "테스트상대", 3, LocalDateTime.now())));
            case VERIFICATION -> sendVerificationCode(toEmail, "123456");
            case CONCERT_REMINDER -> sendConcertReminderNotification(toEmail, "테스터",
                    List.of(new ConcertReminderSummary(0L, "테스트 콘서트", "테스트 공연장", LocalDate.now().plusDays(1))));
            case NEW_CHAT -> sendNewChatNotification(toEmail, "테스터", "테스트상대", "테스트 콘서트");
            case SETLIST_RESULT -> sendSetlistResultNotification(toEmail, "테스터", 0L, "테스트 콘서트");
        }
    }
}
