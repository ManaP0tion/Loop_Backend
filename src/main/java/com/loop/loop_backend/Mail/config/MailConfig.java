package com.loop.loop_backend.Mail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * 발신 계정을 메일 종류별로 분리: 신고/문의 알림은 기존 지메일, 그 외(공연 리마인드/미확인 채팅/인증코드)는 SES.
 * SES는 미검증 발신자를 거부하므로 SES 경유 메일은 반드시 검증된 주소로 setFrom 해야 한다.
 */
@Configuration
public class MailConfig {

    @Bean
    @Primary
    public JavaMailSender gmailMailSender(
            @Value("${spring.mail.host}") String host,
            @Value("${spring.mail.port}") int port,
            @Value("${spring.mail.username}") String username,
            @Value("${spring.mail.password}") String password) {
        return buildSender(host, port, username, password);
    }

    @Bean
    public JavaMailSender sesMailSender(
            @Value("${ses.mail.host}") String host,
            @Value("${ses.mail.port}") int port,
            @Value("${ses.mail.username}") String username,
            @Value("${ses.mail.password}") String password) {
        return buildSender(host, port, username, password);
    }

    private JavaMailSender buildSender(String host, int port, String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        return sender;
    }
}