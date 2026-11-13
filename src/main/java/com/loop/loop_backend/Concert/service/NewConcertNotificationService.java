package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Mail.dto.NewConcertSummary;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.repository.MailRecipient;
import com.loop.loop_backend.User.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 새 공연 알림(NO.47) 대상 선정. 공개 후 1시간이 지난 공연을 모아 공연당 한 번만 처리한다.
 * - 1시간: 공개 직후 관리자가 고치거나 비공개로 되돌릴 시간. 그 사이 비공개가 되면 대상에서 빠진다.
 * - 24시간 상한: 이 기능 배포 전에 공개된 공연이나, 서버가 오래 내려가 있던 동안 공개된 공연을 한꺼번에 보내지 않는다.
 * - 이미 끝난 공연(셋리스트·후기용으로 등록한 지난 공연)은 보내지 않는다.
 * 메일 발송은 NewConcertNotificationScheduler가 이 트랜잭션이 끝난 뒤 한다.
 */
@Service
@RequiredArgsConstructor
public class NewConcertNotificationService {

    static final Duration PUBLISH_DELAY = Duration.ofHours(1);
    static final Duration MAX_PUBLISHED_AGE = Duration.ofHours(24);

    private final ConcertRepository concertRepository;
    private final UserRepository userRepository;

    /**
     * 대상 공연을 골라 처리 시각을 기록한다. 메일보다 먼저 기록(커밋)해 두면 발송 도중 서버가 죽어도
     * 같은 공연 메일이 두 번 나가지 않는다(대신 일부 수신자가 못 받을 수 있다 - 홍보성 알림이라 중복보다 누락을 택했다).
     * @param now 기준 시각. publishedAt과 같은 서버 기본 시간대여야 한다(호출한 쪽이 넘겨 테스트에서 고정)
     */
    @Transactional
    public List<NewConcertSummary> claimDueConcerts(LocalDateTime now) {
        List<Concert> targets = concertRepository.findNewConcertNotificationTargets(
                now.minus(MAX_PUBLISHED_AGE), now.minus(PUBLISH_DELAY), now.toLocalDate());
        targets.forEach(concert -> concert.markNewConcertNotified(now));
        return targets.stream().map(NewConcertSummary::of).toList();
    }

    public List<MailRecipient> findRecipients() {
        return userRepository.findNewConcertMailRecipients(Status.ACTIVE);
    }
}
