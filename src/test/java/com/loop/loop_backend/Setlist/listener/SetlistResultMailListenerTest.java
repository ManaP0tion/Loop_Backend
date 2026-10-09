package com.loop.loop_backend.Setlist.listener;

import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.Setlist.event.SetlistResultSavedEvent;
import com.loop.loop_backend.Setlist.repository.SetlistResultRecipient;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
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
    void 대상_전원에게_공연_정보를_담아_보낸다() {
        when(voteRepository.findResultMailRecipients(1L)).thenReturn(List.of(
                new SetlistResultRecipient("a@loop.com", "에이"), new SetlistResultRecipient("b@loop.com", "비")));

        listener.handle(new SetlistResultSavedEvent(1L, "YUURI LIVE"));

        verify(mailService).sendSetlistResultNotification("a@loop.com", "에이", 1L, "YUURI LIVE");
        verify(mailService).sendSetlistResultNotification("b@loop.com", "비", 1L, "YUURI LIVE");
    }

    @Test
    void 대상이_없으면_보내지_않는다() {
        when(voteRepository.findResultMailRecipients(1L)).thenReturn(List.of());

        listener.handle(new SetlistResultSavedEvent(1L, "YUURI LIVE"));

        verify(mailService, never()).sendSetlistResultNotification(anyString(), any(), anyLong(), any());
    }
}
