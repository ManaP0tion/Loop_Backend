package com.loop.loop_backend.HashTag.repository;

import com.loop.loop_backend.HashTag.domain.UserHashtag;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

// 동행 프로필 목록 성격 조회 요구사항:
// - 여러 작성자의 성격을 한 번에 가져온다. 요청하지 않은 사용자의 성격은 섞이지 않는다.
// - 작성자마다 저장한 순서(성격 교체 때 보낸 순서)대로 나온다.
@DataJpaTest
class UserHashtagsByUsersQueryTest {

    @Autowired private UserHashtagRepository userHashtagRepository;
    @Autowired private UserRepository userRepository;

    private User user(String providerId) {
        return userRepository.save(User.builder().authProvider(AuthProvider.KAKAO).providerId(providerId).build());
    }

    private void tag(User user, String tag) {
        userHashtagRepository.save(UserHashtag.builder().user(user).tag(tag).build());
    }

    @Test
    @DisplayName("요청한 작성자들의 성격만 저장한 순서대로 가져온다")
    void findAllByUserIdIn_ReturnsRequestedUsersInSavedOrder() {
        // given
        User a = user("a");
        User b = user("b");
        User notRequested = user("c");
        tag(a, "외향형");
        tag(b, "낯가림");
        tag(a, "계획형");
        tag(notRequested, "즉흥형");

        // when
        List<UserHashtag> tags = userHashtagRepository.findAllByUser_IdInOrderByIdAsc(List.of(a.getId(), b.getId()));

        // then
        assertThat(tags).extracting(t -> t.getUser().getId(), UserHashtag::getTag).containsExactly(
                tuple(a.getId(), "외향형"),
                tuple(b.getId(), "낯가림"),
                tuple(a.getId(), "계획형"));
    }
}
