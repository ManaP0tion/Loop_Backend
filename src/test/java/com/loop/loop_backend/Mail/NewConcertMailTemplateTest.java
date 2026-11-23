package com.loop.loop_backend.Mail;

import com.loop.loop_backend.Mail.dto.NewConcertSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 새 공연 소식 메일 요구사항:
// - 제목은 "[Loop 신규 공연] 공연명". 여러 공연을 한 통에 묶으면 첫 공연명 뒤에 "외 N건"
// - 광고성 정보라 하단에 수신거부 안내를 한글·영문으로 넣고, [수신거부]·[Unsubscribe]는 같은 링크로 연결한다
class NewConcertMailTemplateTest {

    private static final String FRONTEND = "https://loop.example";
    private static final String UNSUBSCRIBE = FRONTEND + "/settings/notifications";

    private static final NewConcertSummary YOASOBI = new NewConcertSummary(
            56L, "YOASOBI — ASIA-OCEANIA TOUR “초행성”", "단독 공연", "2026.12.24", "인스파이어 아레나");
    private static final NewConcertSummary FESTIVAL = new NewConcertSummary(
            57L, "루프 뮤직 페스티벌", "페스티벌", "2026.12.30 ~ 2026.12.31", "난지 한강공원");

    private final SpringTemplateEngine engine = engine();

    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private String render(List<NewConcertSummary> concerts) {
        Context context = new Context();
        context.setVariable("recipientNickname", "루프");
        context.setVariable("concerts", concerts);
        context.setVariable("frontendUrl", FRONTEND);
        context.setVariable("unsubscribeUrl", UNSUBSCRIBE);
        return engine.process("mail/new-concert-notification", context);
    }

    @Test
    @DisplayName("공연 하나면 제목은 [Loop 신규 공연] 뒤에 공연명이다")
    void subject_SingleConcert() {
        assertThat(NewConcertSummary.mailSubject(List.of(YOASOBI)))
                .isEqualTo("[Loop 신규 공연] YOASOBI — ASIA-OCEANIA TOUR “초행성”");
    }

    @Test
    @DisplayName("여러 공연을 묶으면 첫 공연명 뒤에 나머지 개수를 붙인다")
    void subject_MultipleConcerts() {
        assertThat(NewConcertSummary.mailSubject(List.of(YOASOBI, FESTIVAL)))
                .isEqualTo("[Loop 신규 공연] YOASOBI — ASIA-OCEANIA TOUR “초행성” 외 1건");
    }

    @Test
    @DisplayName("하단에 광고성 정보 안내와 한글·영문 수신거부 안내가 있다")
    void footer_ContainsAdNoticeInKoreanAndEnglish() {
        String html = render(List.of(YOASOBI));

        assertThat(html).contains(
                "본 메일은 광고성 정보 수신에 동의하신 분께 발송됩니다.",
                "더 이상 새 공연 소식 메일을 받지 않으려면",
                "[수신거부]",
                "If you no longer wish to receive these emails, click",
                "[Unsubscribe]");
    }

    @Test
    @DisplayName("[수신거부]와 [Unsubscribe]는 같은 수신거부 링크로 연결된다")
    void footer_BothLinksPointToUnsubscribeUrl() {
        String html = render(List.of(YOASOBI));

        String link = "href=\"" + UNSUBSCRIBE + "\"";
        assertThat(html.split(java.util.regex.Pattern.quote(link), -1)).hasSize(3);
    }

    @Test
    @DisplayName("묶인 공연마다 공연명과 공연 링크가 들어간다")
    void body_ContainsEachConcert() {
        String html = render(List.of(YOASOBI, FESTIVAL));

        assertThat(html).contains("YOASOBI — ASIA-OCEANIA TOUR “초행성”", "루프 뮤직 페스티벌",
                "href=\"https://loop.example/concerts/56\"", "href=\"https://loop.example/concerts/57\"");
    }
}