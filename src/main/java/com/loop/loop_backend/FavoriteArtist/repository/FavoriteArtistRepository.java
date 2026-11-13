package com.loop.loop_backend.FavoriteArtist.repository;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.FavoriteArtist.domain.FavoriteArtist;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FavoriteArtistRepository extends JpaRepository<FavoriteArtist, Long> {

    List<FavoriteArtist> findAllByUser(User user);

    int countByUser(User user);

    boolean existsByUserAndArtist(User user, Artist artist);

    Optional<FavoriteArtist> findByIdAndUser(Long id, User user);

    void deleteAllByUser(User user);

    // 동행 풀 정렬(좋아하는 아티스트 공통 수): 여러 사용자의 관심 아티스트를 쿼리 한 번으로 가져온다
    @Query("select new com.loop.loop_backend.FavoriteArtist.repository.UserArtistId(fa.user.id, fa.artist.id) " +
            "from FavoriteArtist fa where fa.user.id in :userIds")
    List<UserArtistId> findArtistIdsByUserIdIn(@Param("userIds") Collection<Long> userIds);
}