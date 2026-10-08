package com.loop.loop_backend.Mail;

import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import static org.assertj.core.api.Assertions.assertThat;

// 셋리스트 결과 메일(NO.69): 공연명·공연 후 페이지 링크·수신거부(알림 설정) 링크가 들어가고, 적중률 수치는 넣지 않는다.
class SetlistResultMailTemplateTest {

    @Test
    void 공연_링크와_수신거부_링크가_렌더링된다() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        Context context = new Context();
        context.setVariable("recipientNickname", "루프");
        context.setVariable("concertId", 42L);
        context.setVariable("concertTitle", "YUURI LIVE");
        context.setVariable("frontendUrl", "https://loop.example");

        String html = engine.process("mail/setlist-result-notification", context);

        assertThat(html).contains("YUURI LIVE", "루프",
                "href=\"https://loop.example/concerts/42\"",
                "href=\"https://loop.example/settings/notifications\"");
        assertThat(html).doesNotContain("%");
    }
}
