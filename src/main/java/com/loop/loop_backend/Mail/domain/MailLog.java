package com.loop.loop_backend.Mail.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 메일 발송 이력(메타데이터만). 본문·인증코드는 저장하지 않는다 — 보관 최소화.
 * 실패 건도 남겨서 "메일이 안 갔다" 추적에 쓴다(success=false, failReason).
 */
@Entity
@Table(name = "mail_logs", indexes = {
        @Index(name = "idx_mail_logs_sent", columnList = "sent_at"),
        @Index(name = "idx_mail_logs_type", columnList = "type")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private MailType type;

    @Column(name = "to_email", length = 255)
    private String toEmail;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "fail_reason", columnDefinition = "TEXT")
    private String failReason;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    @Builder
    private MailLog(MailType type, String toEmail, String subject, boolean success, String failReason) {
        this.type = type;
        this.toEmail = toEmail;
        this.subject = subject;
        this.success = success;
        this.failReason = failReason;
    }
}
