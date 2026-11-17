package com.loop.loop_backend.Mail;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.TicketVendorInfo;
import com.loop.loop_backend.Mail.dto.TicketAlarmMail;
import com.loop.loop_backend.Mail.dto.TicketAlarmTiming;
import com.loop.loop_backend.TicketAlarm.domain.TicketAlarmType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 예매 알림 메일 요구사항:
// - 예매 유형(선예매/일반예매)별로 따로 보내며, 메일에는 유형·공연명·예매 오픈 일시·공연 기간·공연장·예매처·공연 링크가 들어간다
// - 하루 전(오후 9시) 메일과 시작 30분 전 메일은 내용이 같고 제목·안내 문구만 다르다
// - 링크가 있는 예매처는 링크로, 링크가 없는 예매처(공식 안내 등)는 이름만 보여준다
// - 메일 하단에 수신거부 링크를 넣는다
class TicketAlarmMailTemplateTest {

    private static final String FRONTEND = "https://loop.example";

    // 2026-12-02는 수요일
    private static final LocalDateTime OPENS_AT = LocalDateTime.of(2026, 12, 2, 20, 0);

    private final SpringTemplateEngine engine = engine();

    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private Concert concert() {
        Concert concert = Concert.builder()
                .title("YUURI LIVE")
                .category(ConcertCategory.STAGE)
                .startDate(LocalDate.of(2026, 12, 24))
                .endDate(LocalDate.of(2026, 12, 25))
                .venue("잠실 실내체육관")
                .build();
        ReflectionTestUtils.setField(concert, "id", 42L);
        return concert;
    }

    private TicketAlarmMail mail(TicketAlarmType type, List<TicketVendorInfo> vendors) {
        return TicketAlarmMail.of(concert(), type, OPENS_AT, vendors);
    }

    private String render(TicketAlarmMail mail, TicketAlarmTiming timing, String unsubscribeUrl) {
        Context context = new Context();
        context.setVariable("recipientNickname", "루프");
        context.setVariable("headline", timing.headline(mail));
        context.setVariable("intro", timing.intro(mail));
        context.setVariable("mail", mail);
        context.setVariable("unsubscribeUrl", unsubscribeUrl);
        context.setVariable("frontendUrl", FRONTEND);
        return engine.process("mail/ticket-alarm-notification", context);
    }

    @Test
    @DisplayName("하루 전 메일에 예매 유형·공연명·오픈 일시·기간·공연장·공연 링크가 들어간다")
    void dayBefore_ContainsSaleInfo() {
        String html = render(mail(TicketAlarmType.PRESALE, List.of()), TicketAlarmTiming.DAY_BEFORE, null);

        assertThat(html).contains("루프", "내일 선예매가 열려요", "선예매가 내일 시작돼요",
                "YUURI LIVE", "12월 2일 (수) 오후 8:00 오픈",
                "2026.12.24 ~ 2026.12.25", "잠실 실내체육관",
                "href=\"https://loop.example/concerts/42\"");
    }

    @Test
    @DisplayName("30분 전 메일은 곧 시작한다는 문구가 들어가고 내용은 같다")
    void soon_ContainsSoonWording() {
        String html = render(mail(TicketAlarmType.PRESALE, List.of()), TicketAlarmTiming.SOON, null);

        assertThat(html).contains("곧 선예매가 열려요", "선예매가 30분 뒤 시작돼요",
                "YUURI LIVE", "12월 2일 (수) 오후 8:00 오픈");
        assertThat(html).doesNotContain("내일");
    }

    @Test
    @DisplayName("일반예매 메일은 유형 이름만 일반예매로 바뀐다")
    void generalSale_UsesItsTypeName() {
        String html = render(mail(TicketAlarmType.GENERAL_SALE, List.of()), TicketAlarmTiming.DAY_BEFORE, null);

        assertThat(html).contains("내일 일반예매가 열려요", "일반예매가 내일 시작돼요");
        assertThat(html).doesNotContain("선예매");
    }

    @Test
    @DisplayName("링크가 있는 예매처는 링크로, 링크가 없는 예매처는 이름만 보여준다")
    void vendors_LinkOnlyWhenUrlExists() {
        String html = render(mail(TicketAlarmType.PRESALE, List.of(
                        new TicketVendorInfo("NOL 티켓", "https://tickets.example/nol"),
                        new TicketVendorInfo("공식 팬클럽 안내", null))),
                TicketAlarmTiming.DAY_BEFORE, null);

        assertThat(html).contains("href=\"https://tickets.example/nol\"", "NOL 티켓", "공식 팬클럽 안내");
        assertThat(html).doesNotContainPattern("<a[^>]*>\\s*공식 팬클럽 안내");
    }

    @Test
    @DisplayName("수신거부 링크가 있으면 하단에 넣고, 없으면 수신거부 안내를 넣지 않는다")
    void unsubscribeLink() {
        TicketAlarmMail mail = mail(TicketAlarmType.PRESALE, List.of());

        assertThat(render(mail, TicketAlarmTiming.DAY_BEFORE, "https://loop.example/unsubscribe?t=abc"))
                .contains("href=\"https://loop.example/unsubscribe?t=abc\"", "수신거부");
        assertThat(render(mail, TicketAlarmTiming.DAY_BEFORE, null)).doesNotContain("수신거부");
    }

    @Test
    @DisplayName("제목: 하루 전은 내일 오픈 시각, 30분 전은 30분 뒤라고 알려주고 둘 다 공연명과 예매 유형이 들어간다")
    void subjects() {
        TicketAlarmMail mail = mail(TicketAlarmType.PRESALE, List.of());

        assertThat(TicketAlarmTiming.DAY_BEFORE.subject(mail))
                .isEqualTo("[Loop] 내일 오후 8:00, YUURI LIVE 선예매가 시작돼요");
        assertThat(TicketAlarmTiming.SOON.subject(mail))
                .isEqualTo("[Loop] 30분 뒤 YUURI LIVE 선예매가 시작돼요");
    }

    @Test
    @DisplayName("오전 오픈과 하루짜리 공연도 같은 형식으로 보여준다")
    void morningOpenAndSingleDayConcert() {
        Concert oneDay = Concert.builder().title("하루 공연").category(ConcertCategory.STAGE)
                .startDate(LocalDate.of(2026, 12, 24)).endDate(LocalDate.of(2026, 12, 24)).build();
        TicketAlarmMail mail = TicketAlarmMail.of(oneDay, TicketAlarmType.GENERAL_SALE,
                LocalDateTime.of(2026, 12, 3, 10, 30), List.of());

        assertThat(mail.opensAt()).isEqualTo("12월 3일 (목) 오전 10:30");
        assertThat(mail.period()).isEqualTo("2026.12.24");
    }
}
