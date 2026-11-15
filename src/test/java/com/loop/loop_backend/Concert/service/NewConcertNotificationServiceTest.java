package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Mail.dto.NewConcertSummary;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.MailRecipient;
import com.loop.loop_backend.User.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 새 공연 알림(NO.47) 요구사항 - 대상 공연:
// - 공개 후 1시간이 지난 공연을 대상으로 한다. 1시간이 안 됐으면 다음 실행으로 넘어간다.
// - 1시간 안에 비공개로 되돌리면 대상이 아니다.
// - 공연당 한 번만: 처리한 공연은 다시 실행해도, 비공개 후 재공개해도 다시 대상이 되지 않는다.
// - 이미 끝난 공연은 대상이 아니다(오늘 끝나는 공연은 대상).
// - 공개된 지 24시간이 넘은 공연(기능 배포 전 공개, 서버 장기 중단)은 대상이 아니다.
// - 단독 공연과 페스티벌 모두 대상이고, 한 번 실행에 여러 공연을 함께 처리한다.
// 수신자:
// - 새 공연 알림 설정 ON + 이메일 인증 + 활동 중인 사용자만. 설정 OFF(기본값), 이메일 없음, 정지·탈퇴는 받지 않는다.
@DataJpaTest
class NewConcertNotificationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Autowired private ConcertRepository concertRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private NewConcertNotificationService service;

    @BeforeEach
    void setUp() {
        service = new NewConcertNotificationService(concertRepository, userRepository);
    }

    private Concert publishedAt(String title, ConcertCategory category, LocalDate endDate, LocalDateTime publishedAt) {
        Concert concert = Concert.builder()
                .title(title).category(category)
                .startDate(endDate.minusDays(1)).endDate(endDate)
                .build();
        concert.changePublished(true, publishedAt);
        return concertRepository.save(concert);
    }

    private Concert publishedAt(String title, LocalDateTime publishedAt) {
        return publishedAt(title, ConcertCategory.STAGE, TODAY.plusDays(30), publishedAt);
    }

    private List<NewConcertSummary> claim(LocalDateTime now) {
        List<NewConcertSummary> claimed = service.claimDueConcerts(now);
        em.flush();
        em.clear();
        return claimed;
    }

    private Concert reload(Concert concert) {
        return concertRepository.findById(concert.getId()).orElseThrow();
    }

    private User user(String nickname, String email, boolean newConcertEmail) {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO).providerId("kakao-" + nickname).status(Status.ACTIVE)
                .build();
        user.assignNickname(nickname);
        if (email != null) user.verifyEmail(email);
        user.updateNotificationSettings(null, null, null, newConcertEmail);
        return userRepository.save(user);
    }

    // ── 대상 공연 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("공개 후 1시간이 지난 공연은 대상이 되고 처리 시각이 기록된다")
    void publishedOverAnHourAgo_IsClaimed() {
        Concert concert = publishedAt("YUURI LIVE", NOW.minusHours(1));

        List<NewConcertSummary> claimed = claim(NOW);

        assertThat(claimed).extracting(NewConcertSummary::concertId).containsExactly(concert.getId());
        assertThat(reload(concert).getNewConcertNotifiedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("공개 후 1시간이 안 된 공연은 이번에는 빠지고, 1시간이 지난 뒤 실행에서 대상이 된다")
    void publishedWithinAnHour_IsClaimedLater() {
        Concert concert = publishedAt("YUURI LIVE", NOW.minusMinutes(50));

        assertThat(claim(NOW)).isEmpty();
        assertThat(reload(concert).getNewConcertNotifiedAt()).isNull();

        assertThat(claim(NOW.plusMinutes(10))).extracting(NewConcertSummary::concertId)
                .containsExactly(concert.getId());
    }

    @Test
    @DisplayName("공개 후 1시간 안에 비공개로 되돌린 공연은 대상이 아니다")
    void unpublishedWithinAnHour_IsNotClaimed() {
        Concert concert = publishedAt("YUURI LIVE", NOW.minusMinutes(30));
        concert.changePublished(false, NOW.minusMinutes(10));
        concertRepository.save(concert);

        assertThat(claim(NOW.plusHours(1))).isEmpty();
    }

    @Test
    @DisplayName("한 번 처리한 공연은 다시 실행해도, 비공개 후 다시 공개해도 대상이 되지 않는다")
    void claimedConcert_IsNeverClaimedAgain() {
        Concert concert = publishedAt("YUURI LIVE", NOW.minusHours(2));
        assertThat(claim(NOW)).hasSize(1);

        assertThat(claim(NOW.plusMinutes(10))).isEmpty();

        Concert reloaded = reload(concert);
        reloaded.changePublished(false, NOW.plusMinutes(20));
        reloaded.changePublished(true, NOW.plusMinutes(30));
        concertRepository.save(reloaded);
        em.flush();
        assertThat(claim(NOW.plusHours(3))).isEmpty();
    }

    @Test
    @DisplayName("이미 끝난 공연은 대상이 아니고, 오늘 끝나는 공연은 대상이다")
    void endedConcert_IsNotClaimed() {
        publishedAt("지난 공연", ConcertCategory.STAGE, TODAY.minusDays(1), NOW.minusHours(2));
        Concert endsToday = publishedAt("오늘 끝나는 공연", ConcertCategory.STAGE, TODAY, NOW.minusHours(2));

        assertThat(claim(NOW)).extracting(NewConcertSummary::concertId).containsExactly(endsToday.getId());
    }

    @Test
    @DisplayName("공개된 지 24시간이 넘은 공연은 대상이 아니다")
    void publishedOver24HoursAgo_IsNotClaimed() {
        publishedAt("오래전 공개", NOW.minusHours(25));
        Concert withinDay = publishedAt("하루 안에 공개", NOW.minusHours(23));

        assertThat(claim(NOW)).extracting(NewConcertSummary::concertId).containsExactly(withinDay.getId());
    }

    @Test
    @DisplayName("공개 시각 기록이 없는 기존 공개 공연과 비공개 공연은 대상이 아니다")
    void legacyOrUnpublishedConcert_IsNotClaimed() {
        concertRepository.save(Concert.builder()
                .title("기존 공개 공연").category(ConcertCategory.STAGE).published(true)
                .startDate(TODAY.plusDays(10)).endDate(TODAY.plusDays(10)).build());
        concertRepository.save(Concert.builder()
                .title("비공개 공연").category(ConcertCategory.STAGE)
                .startDate(TODAY.plusDays(10)).endDate(TODAY.plusDays(10)).build());

        assertThat(claim(NOW)).isEmpty();
    }

    @Test
    @DisplayName("단독 공연과 페스티벌을 한 번 실행에서 함께 처리하고, 메일에 쓸 유형·기간이 담긴다")
    void claimsSoloAndFestivalTogether() {
        Concert solo = publishedAt("YUURI LIVE", ConcertCategory.STAGE, TODAY.plusDays(30), NOW.minusHours(3));
        Concert festival = publishedAt("JAPAN FES", ConcertCategory.FESTIVAL, TODAY.plusDays(40), NOW.minusHours(2));

        List<NewConcertSummary> claimed = claim(NOW);

        assertThat(claimed).extracting(NewConcertSummary::concertId, NewConcertSummary::categoryName)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(solo.getId(), "단독 공연"),
                        org.assertj.core.groups.Tuple.tuple(festival.getId(), "페스티벌"));
        assertThat(claimed).filteredOn(c -> c.concertId().equals(festival.getId()))
                .extracting(NewConcertSummary::period)
                .containsExactly("2026.12.29 ~ 2026.12.30");
        assertThat(reload(solo).getNewConcertNotifiedAt()).isEqualTo(NOW);
        assertThat(reload(festival).getNewConcertNotifiedAt()).isEqualTo(NOW);
    }

    // ── 수신자 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("새 공연 알림을 켜고 이메일을 인증한 활동 중인 사용자만 받는다")
    void recipients_OnlyActiveUsersWithEmailAndSettingOn() {
        user("받는사람", "on@loop.com", true);
        user("설정OFF", "off@loop.com", false);
        user("이메일없음", null, true);
        User suspended = user("정지", "suspended@loop.com", true);
        suspended.suspend(null);
        User terminated = user("강제탈퇴", "terminated@loop.com", true);
        terminated.terminate();
        em.flush();
        em.clear();

        List<MailRecipient> recipients = service.findRecipients();

        assertThat(recipients).containsExactly(new MailRecipient("on@loop.com", "받는사람"));
    }

    @Test
    @DisplayName("새 사용자는 새 공연 알림이 꺼져 있어 받지 않는다")
    void recipients_NewUserDefaultOff() {
        User user = User.builder()
                .authProvider(AuthProvider.KAKAO).providerId("kakao-new").status(Status.ACTIVE)
                .build();
        user.verifyEmail("new@loop.com");
        userRepository.save(user);

        assertThat(service.findRecipients()).isEmpty();
    }
}
