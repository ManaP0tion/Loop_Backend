package com.loop.loop_backend.Mail.listener;

import com.loop.loop_backend.Inquiry.event.InquiryCreatedEvent;
import com.loop.loop_backend.Mail.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InquiryMailEventListener {

    private final MailService mailService;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInquiryCreated(InquiryCreatedEvent event) {
        mailService.sendInquiryNotification(
                event.inquiryId(), event.userNickname(), event.type().getLabel(), event.title(), event.content(), event.email());
    }
}