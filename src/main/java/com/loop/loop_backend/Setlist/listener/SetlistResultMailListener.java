package com.loop.loop_backend.Setlist.listener;

import com.loop.loop_backend.Mail.service.MailService;
import com.loop.loop_backend.Setlist.event.SetlistResultSavedEvent;
import com.loop.loop_backend.Setlist.repository.SetlistResultRecipient;
import com.loop.loop_backend.Setlist.repository.SetlistVoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/** 실제 셋리스트 최초 저장 커밋 후 결과 메일 발송(NO.69). 메일 하나가 실패해도 나머지는 보낸다(dispatch가 실패를 로그로 삼킴). */
@Slf4j
@Component
@RequiredArgsConstructor
public class SetlistResultMailListener {

    private final SetlistVoteRepository voteRepository;
    private final MailService mailService;

    // ponytail: 대상 전원을 한 스레드에서 순차 발송. 수천 명 단위면 배치·SES 대량 발송으로
    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SetlistResultSavedEvent event) {
        List<SetlistResultRecipient> recipients = voteRepository.findResultMailRecipients(event.concertId());
        for (SetlistResultRecipient r : recipients) {
            mailService.sendSetlistResultNotification(r.email(), r.nickname(), event.concertId(), event.concertTitle());
        }
        log.info("셋리스트 결과 메일 발송 완료 (concertId={}, recipients={})", event.concertId(), recipients.size());
    }
}
