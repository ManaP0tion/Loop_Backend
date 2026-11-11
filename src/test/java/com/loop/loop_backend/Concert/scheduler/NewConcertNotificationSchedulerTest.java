package com.loop.loop_backend.Concert.scheduler;

import com.loop.loop_backend.Concert.service.NewConcertNotificationService;
import com.loop.loop_backend.Mail.dto.NewConcertSummary;
import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.User.repository.MailRecipient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 새 공연 알림(NO.47) 발송 요구사항:
// - 한 번 실행에서 모인 공연은 수신자마다 메일 1통에 모두 담는다.
// - 대상 공연이 없으면 아무에게도 보내지 않는다.
// - 한 수신자에게 발송이 실패해도 나머지 수신자는 받는다.
class NewConcertNotificationSchedulerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private NewConcertNotificationService service;
    private MailService mailService;
    private NewConcertNotificationScheduler scheduler;

    private final NewConcertSummary solo = new NewConcertSummary(1L, "YUURI LIVE", "단독 공연", "2026.12.24", "KSPO DOME");
    private final NewConcertSummary festival = new NewConcertSummary(2L, "JAPAN FES", "페스티벌", "2026.12.30 ~ 2026.12.31", "인스파이어 아레나");

    @BeforeEach
    void setUp() {
        service = mock(NewConcertNotificationService.class);
        mailService = mock(MailService.class);
        scheduler = new NewConcertNotificationScheduler(service, mailService);
    }

    @Test
    @DisplayName("공연이 여러 개 모이면 수신자마다 메일 1통에 모두 담아 보낸다")
    void sendsOneMailPerRecipientWithAllConcerts() {
        when(service.claimDueConcerts(NOW)).thenReturn(List.of(solo, festival));
        when(service.findRecipients()).thenReturn(List.of(
                new MailRecipient("a@loop.com", "에이"), new MailRecipient("b@loop.com", "비")));

        scheduler.notifyNewConcerts(NOW);

        verify(mailService).sendNewConcertNotification("a@loop.com", "에이", List.of(solo, festival));
        verify(mailService).sendNewConcertNotification("b@loop.com", "비", List.of(solo, festival));
        verify(mailService, times(2)).sendNewConcertNotification(anyString(), anyString(), anyList());
    }

    @Test
    @DisplayName("대상 공연이 없으면 아무에게도 보내지 않는다")
    void noConcerts_SendsNothing() {
        when(service.claimDueConcerts(NOW)).thenReturn(List.of());

        scheduler.notifyNewConcerts(NOW);

        verify(service, never()).findRecipients();
        verify(mailService, never()).sendNewConcertNotification(any(), any(), any());
    }

    @Test
    @DisplayName("한 수신자에게 발송이 실패해도 나머지 수신자는 받는다")
    void oneFailure_DoesNotStopOthers() {
        when(service.claimDueConcerts(NOW)).thenReturn(List.of(solo));
        when(service.findRecipients()).thenReturn(List.of(
                new MailRecipient("fail@loop.com", "실패"), new MailRecipient("ok@loop.com", "성공")));
        doThrow(new RuntimeException("template error"))
                .when(mailService).sendNewConcertNotification(eq("fail@loop.com"), any(), any());

        scheduler.notifyNewConcerts(NOW);

        verify(mailService).sendNewConcertNotification("ok@loop.com", "성공", List.of(solo));
    }
}
