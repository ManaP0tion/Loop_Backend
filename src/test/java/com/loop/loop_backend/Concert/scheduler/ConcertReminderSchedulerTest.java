package com.loop.loop_backend.Concert.scheduler;

import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.ConcertReminderRow;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Mail.dto.ConcertReminderSummary;
import com.loop.loop_backend.Mail.service.MailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConcertReminderSchedulerTest {

    @Mock CompanionPostRepository companionPostRepository;
    @Mock MailService mailService;

    @InjectMocks ConcertReminderScheduler scheduler;

    @Test
    void 결과가_비어있으면_메일을_발송하지_않는다() {
        when(companionPostRepository.findConcertReminderRows(any(), any(), any(), any()))
                .thenReturn(List.of());

        scheduler.sendDailyConcertReminder();

        verify(mailService, never()).sendConcertReminderNotification(any(), any(), any());
    }

    @Test
    void 유저별로_그룹핑되어_각각_한번씩_메일이_발송된다() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        // 유저1은 두 공연, 유저2는 한 공연 - 정렬은 recipientId 기준
        when(companionPostRepository.findConcertReminderRows(any(), any(), any(), any()))
                .thenReturn(List.of(
                        new ConcertReminderRow(1L, "a@a.com", "닉A", 100L, "공연1", "장소1", tomorrow, WatchDay.DAY1),
                        new ConcertReminderRow(1L, "a@a.com", "닉A", 200L, "공연2", "장소2", tomorrow, WatchDay.DAY1),
                        new ConcertReminderRow(2L, "b@b.com", "닉B", 300L, "공연3", "장소3", tomorrow, WatchDay.DAY1)
                ));

        scheduler.sendDailyConcertReminder();

        ArgumentCaptor<List<ConcertReminderSummary>> captor = ArgumentCaptor.forClass(List.class);
        verify(mailService, times(2))
                .sendConcertReminderNotification(any(), any(), captor.capture());

        List<List<ConcertReminderSummary>> allInvocations = captor.getAllValues();
        assertThat(allInvocations.get(0)).extracting(ConcertReminderSummary::concertId)
                .containsExactly(100L, 200L);
        assertThat(allInvocations.get(1)).extracting(ConcertReminderSummary::concertId)
                .containsExactly(300L);

        verify(mailService).sendConcertReminderNotification(eq("a@a.com"), eq("닉A"), any());
        verify(mailService).sendConcertReminderNotification(eq("b@b.com"), eq("닉B"), any());
    }

    @Test
    void watchDay가_DAY2면_실제_관람일은_startDate에_하루가_더해진다() {
        LocalDate startDate = LocalDate.now();
        when(companionPostRepository.findConcertReminderRows(any(), any(), any(), any()))
                .thenReturn(List.of(
                        new ConcertReminderRow(1L, "a@a.com", "닉A", 100L, "공연1", "장소1", startDate, WatchDay.DAY2)
                ));

        scheduler.sendDailyConcertReminder();

        ArgumentCaptor<List<ConcertReminderSummary>> captor = ArgumentCaptor.forClass(List.class);
        verify(mailService).sendConcertReminderNotification(any(), any(), captor.capture());
        assertThat(captor.getValue()).extracting(ConcertReminderSummary::watchDate)
                .containsExactly(startDate.plusDays(1));
    }
}
