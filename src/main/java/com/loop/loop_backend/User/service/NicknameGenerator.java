package com.loop.loop_backend.User.service;

import com.loop.loop_backend.User.domain.RandomNicknames;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * 아직 아무도 쓰지 않는 랜덤 닉네임을 만든다.
 * 조합(2,601개)에서 최대 10번 뽑아 보고, 그래도 다 차 있으면 숫자 한 자리를 붙여 다시 10번 뽑는다.
 * 확인과 저장 사이에 다른 사람이 같은 닉네임을 가져가면 닉네임 컬럼의 unique 제약이 막는다(드문 경우라 재시도는 하지 않는다).
 */
@Component
public class NicknameGenerator {

    private static final int MAX_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final Supplier<RandomGenerator> random;

    @Autowired
    public NicknameGenerator(UserRepository userRepository) {
        this(userRepository, ThreadLocalRandom::current);
    }

    // 테스트에서 뽑히는 순서를 고정하려고 난수 생성기를 바꿔 끼운다
    NicknameGenerator(UserRepository userRepository, Supplier<RandomGenerator> random) {
        this.userRepository = userRepository;
        this.random = random;
    }

    public String generateUnique() {
        String nickname = tryPick(() -> RandomNicknames.pick(random.get()));
        if (nickname == null) nickname = tryPick(() -> RandomNicknames.pickWithDigit(random.get()));
        if (nickname == null) throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        return nickname;
    }

    private String tryPick(Supplier<String> picker) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = picker.get();
            if (!userRepository.existsByNickname(candidate)) return candidate;
        }
        return null;
    }
}