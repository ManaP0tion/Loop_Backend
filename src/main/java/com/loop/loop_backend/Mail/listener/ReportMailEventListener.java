package com.loop.loop_backend.Mail.listener;

import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.Report.event.ReportCreatedEvent;
import com.loop.loop_backend.Storage.service.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReportMailEventListener {

    // 메일은 접수 직후가 아니라 나중에 열어볼 수도 있어 presigned URL 만료를 넉넉하게 잡음
    private static final Duration IMAGE_LINK_EXPIRATION = Duration.ofHours(1);

    private final MailService mailService;
    private final S3StorageService s3StorageService;

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReportCreated(ReportCreatedEvent event) {
        List<String> presignedImageUrls = event.imageUrls().stream()
                .map(key -> s3StorageService.generatePresignedGetUrl(key, IMAGE_LINK_EXPIRATION))
                .toList();

        mailService.sendReportNotification(
                event.reportId(),
                event.reporterNickname(),
                event.targetNickname(),
                event.reason(),
                event.detail(),
                presignedImageUrls);
    }
}