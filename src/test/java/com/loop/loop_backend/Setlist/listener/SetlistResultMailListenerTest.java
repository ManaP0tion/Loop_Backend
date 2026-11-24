package com.loop.loop_backend.Setlist.listener;

import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.Setlist.event.SetlistResultSavedEvent;
import com.loop.loop_backend.Setlist.repository.SetlistResultRecipient;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// 결과 메일(NO.69): 대상(투표·동의·인증·알림 ON - 리포지토리 쿼리) 전원에게 공연 링크를 담아 보낸다.
class SetlistResultMailListenerTest {

    private final SetlistVoteRepository voteRepository = mock(SetlistVoteRepository.class);
    private final MailService mailService = mock(MailService.class);
    private final SetlistResultMailListener listener = new SetlistResultMailListener(voteRepository, mailService);

    @Test
    @DisplayName("출연 정보의 메일 대상 전원에게 공연 정보를 담아 결과 메일을 보낸다")
    void handle_WhenRecipientsExist_SendsMailToAllWithConcertInfo() {
        // given
        when(voteRepository.findResultMailRecipients(5L)).thenReturn(List.of(
                new SetlistResultRecipient("a@loop.com", "에이"), new SetlistResultRecipient("b@loop.com", "비")));

        // when
        listener.handle(new SetlistResultSavedEvent(1L, 5L, "YUURI LIVE"));

        // then
        verify(mailService).sendSetlistResultNotification("a@loop.com", "에이", 1L, "YUURI LIVE");
        verify(mailService).sendSetlistResultNotification("b@loop.com", "비", 1L, "YUURI LIVE");
    }

    @Test
    @DisplayName("메일 대상이 없으면 결과 메일을 보내지 않는다")
    void handle_WhenNoRecipients_SendsNothing() {
        // given
        when(voteRepository.findResultMailRecipients(5L)).thenReturn(List.of());

        // when
        listener.handle(new SetlistResultSavedEvent(1L, 5L, "YUURI LIVE"));

        // then
        verify(mailService, never()).sendSetlistResultNotification(anyString(), any(), anyLong(), any());
    }
}
