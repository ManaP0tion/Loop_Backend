package com.loop.loop_backend.HashTag.service;

import com.loop.loop_backend.HashTag.domain.UserHashtag;
import com.loop.loop_backend.HashTag.dto.HashtagResponseDto;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 해시태그(성격) 목록 교체 요구사항 (PUT /api/users/me/hashtags):
// - 보낸 목록으로 통째로 교체한다. 빠진 태그는 지워지고, 기존 태그를 일부 유지해도 저장된다.
// - []를 보내면 모두 지운다.
// - 최대 5개. 6개 이상이면 409 LIMIT_HASHTAG, 목록 안에 같은 태그가 있으면 409 DUPLICATE_HASHTAG.
//   실패하면 기존 태그는 그대로 남는다.
// - 다른 사용자의 태그는 건드리지 않는다.
// 엔티티 단위 삭제는 flush 때 INSERT 뒤로 밀려 유니크 제약(user_id, tag)에 걸리므로 실제 DB로 검증한다.
@DataJpaTest
class UserHashtagReplaceTest {

    @Autowired private UserHashtagRepository userHashtagRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager em;

    private UserHashtagService service;
    private Long meId;
    private Long otherId;

    @BeforeEach
    void setUp() {
        service = new UserHashtagServiceImpl(userHashtagRepository, userRepository);
        User me = userRepository.save(User.builder().authProvider(AuthProvider.KAKAO).providerId("kakao-me").build());
        User other = userRepository.save(User.builder().authProvider(AuthProvider.KAKAO).providerId("kakao-other").build());
        meId = me.getId();
        otherId = other.getId();
        saveTags(me, "낯가림", "계획형");
        saveTags(other, "낯가림");
        em.flush();
        em.clear();
    }

    private void saveTags(User user, String... tags) {
        for (String tag : tags) {
            userHashtagRepository.save(UserHashtag.builder().user(user).tag(tag).build());
        }
    }

    private List<String> tagsInDb(Long userId) {
        em.flush();
        em.clear();
        return userHashtagRepository.findAllByUser(userRepository.findById(userId).orElseThrow()).stream()
                .map(UserHashtag::getTag)
                .toList();
    }

    @Test
    @DisplayName("기존 태그를 일부 유지하면서 교체해도 보낸 목록대로 저장된다")
    void replace_KeepingSomeTags_SavesRequestedList() {
        // when
        List<HashtagResponseDto> result = service.replaceHashtags(meId, List.of("낯가림", "즉흥형"));

        // then
        assertThat(result).extracting(HashtagResponseDto::getTag).containsExactlyInAnyOrder("낯가림", "즉흥형");
        assertThat(tagsInDb(meId)).containsExactlyInAnyOrder("낯가림", "즉흥형");
    }

    @Test
    @DisplayName("빈 목록을 보내면 태그가 모두 지워진다")
    void replace_WithEmptyList_RemovesAll() {
        // when
        service.replaceHashtags(meId, List.of());

        // then
        assertThat(tagsInDb(meId)).isEmpty();
    }

    @Test
    @DisplayName("5개까지는 저장된다")
    void replace_WithFiveTags_Saves() {
        // when
        service.replaceHashtags(meId, List.of("낯가림", "계획형", "외향형", "수다쟁이", "조용함"));

        // then
        assertThat(tagsInDb(meId)).hasSize(5);
    }

    @Test
    @DisplayName("6개를 보내면 409 개수 초과이고 기존 태그는 그대로다")
    void replace_WithSixTags_ThrowsLimitAndKeepsExisting() {
        // when & then
        assertThatThrownBy(() -> service.replaceHashtags(meId, List.of("가", "나", "다", "라", "마", "바")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.LIMIT_HASHTAG);
        assertThat(tagsInDb(meId)).containsExactlyInAnyOrder("낯가림", "계획형");
    }

    @Test
    @DisplayName("목록에 같은 태그가 두 번 있으면 409 중복이고 기존 태그는 그대로다")
    void replace_WithDuplicateInRequest_ThrowsDuplicateAndKeepsExisting() {
        // when & then
        assertThatThrownBy(() -> service.replaceHashtags(meId, List.of("외향형", "외향형")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_HASHTAG);
        assertThat(tagsInDb(meId)).containsExactlyInAnyOrder("낯가림", "계획형");
    }

    @Test
    @DisplayName("내 태그를 교체해도 다른 사용자의 태그는 그대로다")
    void replace_DoesNotTouchOtherUsers() {
        // when
        service.replaceHashtags(meId, List.of());

        // then
        assertThat(tagsInDb(otherId)).containsExactly("낯가림");
    }
}