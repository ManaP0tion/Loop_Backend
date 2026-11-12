package com.loop.loop_backend.FavoriteArtist.repository;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.FavoriteArtist.domain.FavoriteArtist;
import com.loop.loop_backend.User.domain.AuthProvider;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 동행 풀 정렬(좋아하는 아티스트 공통 수)용 조회 요구사항:
// - 여러 사용자의 관심 아티스트를 (사용자, 아티스트) 쌍으로 한 번에 가져온다.
// - 요청하지 않은 사용자의 관심 아티스트는 섞이지 않는다.
@DataJpaTest
class FavoriteArtistIdsQueryTest {

    @Autowired private FavoriteArtistRepository favoriteArtistRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ArtistRepository artistRepository;

    private User user(String providerId) {
        return userRepository.save(User.builder().authProvider(AuthProvider.KAKAO).providerId(providerId).build());
    }

    private void favorite(User user, Artist artist) {
        favoriteArtistRepository.save(FavoriteArtist.builder().user(user).artist(artist).build());
    }

    @Test
    @DisplayName("요청한 사용자들의 관심 아티스트만 (사용자, 아티스트) 쌍으로 가져온다")
    void findArtistIdsByUserIdIn_ReturnsOnlyRequestedUsers() {
        // given
        Artist yoasobi = artistRepository.save(Artist.builder().name("YOASOBI").build());
        Artist vaundy = artistRepository.save(Artist.builder().name("Vaundy").build());
        User me = user("me");
        User other = user("other");
        User notRequested = user("not-requested");
        favorite(me, yoasobi);
        favorite(me, vaundy);
        favorite(other, yoasobi);
        favorite(notRequested, vaundy);

        // when
        List<UserArtistId> rows = favoriteArtistRepository.findArtistIdsByUserIdIn(List.of(me.getId(), other.getId()));

        // then
        assertThat(rows).containsExactlyInAnyOrder(
                new UserArtistId(me.getId(), yoasobi.getId()),
                new UserArtistId(me.getId(), vaundy.getId()),
                new UserArtistId(other.getId(), yoasobi.getId()));
    }
}
