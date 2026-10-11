package com.loop.loop_backend.TicketAlarm.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Concert.domain.ticket.ConcertGeneralSale;
import com.loop.loop_backend.Concert.domain.ticket.ConcertPresale;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertGeneralSaleRepository;
import com.loop.loop_backend.Concert.repository.ticket.ConcertPresaleRepository;
import com.loop.loop_backend.ConcertScrap.domain.ConcertScrap;
import com.loop.loop_backend.ConcertScrap.repository.ConcertScrapRepository;
import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;
import com.loop.loop_backend.TicketAlarm.dto.TicketAlarmResponse;
import com.loop.loop_backend.TicketAlarm.repository.TicketAlarmRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 공연 예매 알림 토글 요구사항:
// - 선예매·일반예매 알림을 각각 켜고 끈다. 기본은 꺼짐.
// - 스크랩과 별개다 - 스크랩하지 않아도 켤 수 있고, 스크랩을 해제해도 알림은 그대로다.
// - 그 유형에 예매 일정(예매 일시가 정해진 것)이 없으면 켤 수 없다(400). 일시가 미정인 일정은 사용자에게 보이지 않아 없는 것으로 본다.
// - 끄기는 일정이 없어도 된다.
// - 토글이라 이미 켜진 것을 켜거나 꺼진 것을 꺼도 에러 없이 현재 상태를 돌려준다.
// - 비공개(오픈 예정) 공연은 켤 수 없다(403). 없는 공연은 404.
// - 비로그인 사용자의 알림 상태는 모두 꺼짐이다.
@DataJpaTest
class TicketAlarmServiceTest {

    private static final LocalDateTime OPENS_AT = LocalDateTime.of(2026, 11, 1, 20, 0);

    @Autowired private TicketAlarmRepository ticketAlarmRepository;
    @Autowired private ConcertRepository concertRepository;
    @Autowired private ConcertPresaleRepository presaleRepository;
    @Autowired private ConcertGeneralSaleRepository generalSaleRepository;
    @Autowired private ConcertScrapRepository concertScrapRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private TicketAlarmService service;
    private User me;

    @BeforeEach
    void setUp() {
        service = new TicketAlarmService(ticketAlarmRepository, concertRepository, presaleRepository,
                generalSaleRepository, userRepository);
        me = User.builder().authProvider(AuthProvider.KAKAO).providerId("me").status(Status.ACTIVE)
                .onboardingCompleted(true).build();
        me.completeOnboarding("me", LocalDate.now().minusYears(25), Gender.MALE);
        userRepository.save(me);
    }

    private Concert concert(boolean published) {
        Concert concert = Concert.builder().title("YUURI LIVE").category(ConcertCategory.J_POP_ARTIST)
                .startDate(LocalDate.of(2026, 12, 5)).endDate(LocalDate.of(2026, 12, 5)).build();
        concert.changePublished(published, LocalDateTime.now());
        return concertRepository.save(concert);
    }

    /** 선예매·일반예매 모두 일정이 잡힌 공개 공연 */
    private Concert scheduledConcert() {
        Concert concert = concert(true);
        presale(concert, OPENS_AT);
        generalSale(concert, OPENS_AT.plusDays(7));
        return concert;
    }

    private void presale(Concert concert, LocalDateTime opensAt) {
        presaleRepository.save(ConcertPresale.builder().concert(concert).opensAt(opensAt)
                .vendors(List.of(new TicketVendorInfo("NOL", "https://nol.example"))).build());
    }

    private void generalSale(Concert concert, LocalDateTime opensAt) {
        generalSaleRepository.save(ConcertGeneralSale.builder().concert(concert).opensAt(opensAt)
                .vendors(List.of(new TicketVendorInfo("NOL", "https://nol.example"))).build());
    }

    private static void assertErrorCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // ---------- 기본·켜기·끄기 ----------

    @Test
    void 알림은_기본적으로_꺼져_있다() {
        Concert concert = scheduledConcert();

        assertThat(service.typesOf(me.getId(), concert.getId())).isEmpty();
    }

    @Test
    void 선예매_알림을_켜면_선예매만_켜진다() {
        Concert concert = scheduledConcert();

        TicketAlarmResponse status = service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE);

        assertThat(status).isEqualTo(new TicketAlarmResponse(true, false));
        assertThat(service.typesOf(me.getId(), concert.getId())).containsExactly(TicketAlarmType.PRESALE);
    }

    @Test
    void 선예매와_일반예매_알림은_따로_켜고_끈다() {
        Concert concert = scheduledConcert();
        service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE);
        service.turnOn(me.getId(), concert.getId(), TicketAlarmType.GENERAL_SALE);

        TicketAlarmResponse status = service.turnOff(me.getId(), concert.getId(), TicketAlarmType.PRESALE);

        assertThat(status).isEqualTo(new TicketAlarmResponse(false, true));
    }

    @Test
    void 이미_켜진_알림을_다시_켜도_켜진_상태_그대로다() {
        Concert concert = scheduledConcert();
        service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE);

        TicketAlarmResponse status = service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE);

        assertThat(status).isEqualTo(new TicketAlarmResponse(true, false));
        assertThat(ticketAlarmRepository.count()).isEqualTo(1);
    }

    @Test
    void 꺼진_알림을_꺼도_에러_없이_꺼진_상태다() {
        Concert concert = scheduledConcert();

        TicketAlarmResponse status = service.turnOff(me.getId(), concert.getId(), TicketAlarmType.GENERAL_SALE);

        assertThat(status).isEqualTo(new TicketAlarmResponse(false, false));
    }

    @Test
    void 알림은_사용자마다_따로다() {
        Concert concert = scheduledConcert();
        User other = User.builder().authProvider(AuthProvider.KAKAO).providerId("other").status(Status.ACTIVE)
                .onboardingCompleted(true).build();
        other.completeOnboarding("other", LocalDate.now().minusYears(25), Gender.FEMALE);
        userRepository.save(other);

        service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE);

        assertThat(service.typesOf(other.getId(), concert.getId())).isEmpty();
    }

    // ---------- 스크랩과 별개 ----------

    @Test
    void 스크랩하지_않은_공연도_알림을_켤_수_있다() {
        Concert concert = scheduledConcert();

        service.turnOn(me.getId(), concert.getId(), TicketAlarmType.GENERAL_SALE);

        assertThat(concertScrapRepository.existsByUser_IdAndConcert_Id(me.getId(), concert.getId())).isFalse();
        assertThat(service.typesOf(me.getId(), concert.getId())).containsExactly(TicketAlarmType.GENERAL_SALE);
    }

    @Test
    void 스크랩을_해제해도_알림은_그대로다() {
        Concert concert = scheduledConcert();
        ConcertScrap scrap = concertScrapRepository.save(ConcertScrap.builder().user(me).concert(concert).build());
        service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE);

        concertScrapRepository.delete(scrap);
        em.flush();
        em.clear();

        assertThat(service.typesOf(me.getId(), concert.getId())).containsExactly(TicketAlarmType.PRESALE);
    }

    // ---------- 예매 일정 ----------

    @Test
    void 선예매_일정이_없으면_선예매_알림을_켤_수_없다() {
        Concert concert = concert(true);
        generalSale(concert, OPENS_AT);

        assertErrorCode(() -> service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE),
                ErrorCode.TICKET_SCHEDULE_NOT_FOUND);
        assertThat(service.typesOf(me.getId(), concert.getId())).isEmpty();
    }

    @Test
    void 일반예매_일정이_없으면_일반예매_알림을_켤_수_없다() {
        Concert concert = concert(true);
        presale(concert, OPENS_AT);

        assertErrorCode(() -> service.turnOn(me.getId(), concert.getId(), TicketAlarmType.GENERAL_SALE),
                ErrorCode.TICKET_SCHEDULE_NOT_FOUND);
    }

    @Test
    void 예매_일시가_미정인_일정만_있으면_알림을_켤_수_없다() {
        Concert concert = concert(true);
        presale(concert, null);

        assertErrorCode(() -> service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE),
                ErrorCode.TICKET_SCHEDULE_NOT_FOUND);
    }

    @Test
    void 예매_일정이_없어도_알림은_끌_수_있다() {
        Concert concert = concert(true);

        TicketAlarmResponse status = service.turnOff(me.getId(), concert.getId(), TicketAlarmType.PRESALE);

        assertThat(status).isEqualTo(new TicketAlarmResponse(false, false));
    }

    // ---------- 공연 상태·로그인 ----------

    @Test
    void 비공개_공연은_알림을_켤_수_없다() {
        Concert concert = concert(false);
        presale(concert, OPENS_AT);

        assertErrorCode(() -> service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE),
                ErrorCode.CONCERT_NOT_OPEN);
    }

    @Test
    void 없는_공연은_404() {
        assertErrorCode(() -> service.turnOn(me.getId(), 999_999L, TicketAlarmType.PRESALE), ErrorCode.CONCERT_NOT_FOUND);
        assertErrorCode(() -> service.turnOff(me.getId(), 999_999L, TicketAlarmType.PRESALE), ErrorCode.CONCERT_NOT_FOUND);
    }

    @Test
    void 비로그인_사용자의_알림은_모두_꺼짐이다() {
        Concert concert = scheduledConcert();
        service.turnOn(me.getId(), concert.getId(), TicketAlarmType.PRESALE);

        assertThat(service.typesOf(null, concert.getId())).isEmpty();
    }
}
