package com.loop.loop_backend.Admin.scheduler;

import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 30일 이용정지 자동 해제 (약관 제12조 2항의 영구정지는 suspendedUntil=null 이므로 제외). */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspensionScheduler {

    private final UserRepository userRepository;

    @Scheduled(cron = "0 5 3 * * *", zone = "Asia/Seoul") // 매일 03:05
    @Transactional
    public void autoLift() {
        List<User> due = userRepository.findByStatusAndSuspendedUntilBefore(Status.SUSPENDED, LocalDateTime.now());
        due.forEach(User::liftSuspension);
        if (!due.isEmpty()) log.info("자동 정지 해제: {}건", due.size());
    }
}
