package com.loop.loop_backend.User.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 랜덤 닉네임 조합 요구사항:
// - 앞 단어 51개 × 뒤 단어 51개를 띄어쓰기 없이 붙인다 (서로 다른 2,601개).
// - 모든 조합이, 숫자 한 자리를 붙여도, 닉네임 규칙(8자 이하, 한글·영문·숫자만)을 지킨다.
class RandomNicknamesTest {

    private static final String NICKNAME_RULE = "^[a-zA-Z0-9가-힣]{1,8}$";

    @Test
    @DisplayName("앞·뒤 단어 조합은 서로 다른 2,601개다")
    void combinations_Are2601Distinct() {
        // when
        Set<String> all = new HashSet<>();
        for (String front : RandomNicknames.FRONT) {
            for (String back : RandomNicknames.BACK) {
                all.add(front + back);
            }
        }

        // then
        assertThat(RandomNicknames.FRONT).hasSize(51).doesNotHaveDuplicates();
        assertThat(RandomNicknames.BACK).hasSize(51).doesNotHaveDuplicates();
        assertThat(all).hasSize(2601);
    }

    @Test
    @DisplayName("모든 조합은 숫자 한 자리를 붙여도 닉네임 규칙을 지킨다")
    void everyCombinationWithDigit_FollowsNicknameRule() {
        for (String front : RandomNicknames.FRONT) {
            for (String back : RandomNicknames.BACK) {
                assertThat(front + back).matches(NICKNAME_RULE);
                assertThat(front + back + "9").matches(NICKNAME_RULE);
            }
        }
    }
}