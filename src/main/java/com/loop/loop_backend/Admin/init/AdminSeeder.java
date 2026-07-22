package com.loop.loop_backend.Admin.init;

import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.Role;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** dev 편의: 부팅 시 loginId=admin / password=0000 / role=ADMIN 계정을 보장. prod 에선 절대 실행 안 됨. */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!prod")
public class AdminSeeder implements ApplicationRunner {

    private static final String LOGIN_ID = "admin";
    private static final String PLAIN_PW = "0000";
    private static final String NICKNAME = "관리자";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUserId(LOGIN_ID)) return;

        String nickname = userRepository.existsByNickname(NICKNAME) ? "관리자" + UUID.randomUUID().toString().substring(0, 4) : NICKNAME;

        User admin = User.builder()
                .authProvider(AuthProvider.EMAIL)
                .providerId("seed-admin-" + UUID.randomUUID())
                .onboardingCompleted(true)
                .role(Role.ADMIN)
                .build();
        admin.completeOnboarding(nickname, null, null);
        admin.assignEmailCredentials(LOGIN_ID, passwordEncoder.encode(PLAIN_PW));

        userRepository.save(admin);
        log.info("[dev] Seed 관리자 계정 생성됨 — loginId={} password={}", LOGIN_ID, PLAIN_PW);
    }
}
