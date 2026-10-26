package com.loop.loop_backend.Setlist.service;

import com.loop.loop_backend.Notification.service.DiscordNotificationService;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 실제 셋리스트 등록 리마인드: 어제 끝난 공개 공연 중 실제 셋리스트가 아직 없는 셋리스트 대상(단독 공연, 페스티벌 헤드라이너)을
 * 관리자 디스코드로 한 통 알린다. 하루만(D+1) 알리고, 대상이 없으면 보내지 않는다.
 * 조회에서 공연·아티스트를 함께 가져오므로 트랜잭션 없이 돌린다 - 디스코드 전송 동안 DB 연결을 잡지 않게.
 */
@Service
@RequiredArgsConstructor
public class ActualSetlistReminderService {

    // 디스코드 메시지는 2000자 제한이라 목록 줄 수와 공연명 길이를 제한한다
    static final int MAX_LISTED = 10;
    static final int MAX_TITLE_LENGTH = 80;

    private final PerformanceRepository performanceRepository;
    private final DiscordNotificationService discordNotificationService;

    // 어드민 공연 관리 알림은 KOPIS 채널 하나로 모은다
    @Value("${discord.webhook.kopis}")
    private String webhookUrl;

    /** @param today 기준일(KST). 호출한 쪽(스케줄러)이 넘겨 테스트에서 날짜를 고정할 수 있게 한다 */
    public void remind(LocalDate today) {
        List<Performance> missing = performanceRepository.findMissingActualSetlist(today.minusDays(1));
        if (missing.isEmpty()) return;
        discordNotificationService.send(webhookUrl, message(missing));
    }

    static String message(List<Performance> missing) {
        String lines = missing.stream().limit(MAX_LISTED)
                .map(ActualSetlistReminderService::line)
                .collect(Collectors.joining("\n"));
        String more = missing.size() > MAX_LISTED ? "\n외 " + (missing.size() - MAX_LISTED) + "건" : "";
        return "**실제 셋리스트 등록 필요 " + missing.size() + "건** (어제 종료)\n" + lines + more;
    }

    /** - [아티스트] 공연명. 페스티벌은 헤드라이너라는 표시를 붙인다 */
    private static String line(Performance p) {
        String title = p.getConcert().getTitle();
        if (title.length() > MAX_TITLE_LENGTH) title = title.substring(0, MAX_TITLE_LENGTH) + "…";
        return "- [" + p.getArtist().getName() + "] " + title + (p.getConcert().isFestival() ? " (헤드라이너)" : "");
    }
}