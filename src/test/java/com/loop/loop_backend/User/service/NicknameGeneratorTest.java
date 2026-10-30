package com.loop.loop_backend.User.service;

import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 랜덤 닉네임 중복 요구사항 (조합 규칙은 RandomNicknamesTest):
// - 이미 쓰는 닉네임은 주지 않는다. 조합이 다 차 있으면 숫자 한 자리를 붙인다. 그래도 없으면 409.
class NicknameGeneratorTest {

    private static final String NICKNAME_RULE = "^[a-zA-Z0-9가-힣]{1,8}$";

    private UserRepository userRepository;
    private NicknameGenerator generator;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        RandomGenerator random = new SplittableRandom(42);
        generator = new NicknameGenerator(userRepository, () -> random);
    }

    @Test
    @DisplayName("이미 쓰는 닉네임은 건너뛰고 아무도 쓰지 않는 닉네임을 준다")
    void generateUnique_SkipsTakenNickname() {
        // given - 처음 뽑힌 닉네임만 이미 쓰는 중
        String[] first = new String[1];
        when(userRepository.existsByNickname(anyString())).thenAnswer(invocation -> {
            String candidate = invocation.getArgument(0);
            if (first[0] == null) first[0] = candidate;
            return candidate.equals(first[0]);
        });

        // when
        String nickname = generator.generateUnique();

        // then
        assertThat(nickname).isNotEqualTo(first[0]);
    }

    @Test
    @DisplayName("단어 조합이 모두 차 있으면 숫자 한 자리를 붙인 닉네임을 준다")
    void generateUnique_AllCombinationsTaken_AppendsDigit() {
        // given - 숫자 없는 조합은 모두 사용 중
        when(userRepository.existsByNickname(argThat(n -> !n.matches(".*\\d$")))).thenReturn(true);

        // when
        String nickname = generator.generateUnique();

        // then
        assertThat(nickname).matches(".*[1-9]$").matches(NICKNAME_RULE);
    }

    @Test
    @DisplayName("숫자를 붙여도 쓸 수 있는 닉네임이 없으면 409")
    void generateUnique_NothingAvailable_ThrowsDuplicate() {
        // given
        when(userRepository.existsByNickname(anyString())).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> generator.generateUnique())
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }
}