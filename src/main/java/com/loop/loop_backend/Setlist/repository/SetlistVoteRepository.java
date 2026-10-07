package com.loop.loop_backend.Setlist.repository;

import com.loop.loop_backend.Setlist.domain.SetlistVote;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SetlistVoteRepository extends JpaRepository<SetlistVote, Long> {

    Optional<SetlistVote> findByConcertIdAndUserId(Long concertId, Long userId);

    long countByConcertId(Long concertId);

    // 회원탈퇴 정리용(UserServiceImpl.withdrawUser) - 탈퇴는 users 행을 지우지 않아 FK cascade가 동작하지 않는다
    void deleteAllByUser(User user);

    /**
     * 곡별 득표 수(NO.66). 득표순, 동점이면 곡 정렬 순번 순 - 공동 순위 없이 하이라이트 경계가 호출마다 같게.
     * 소프트 삭제된 곡은 Song의 @SQLRestriction으로 빠진다.
     */
    @Query("""
            select new com.loop.loop_backend.Setlist.repository.SongVoteCount(s.id, count(v))
            from SetlistVote v join v.songs s
            where v.concert.id = :concertId
            group by s.id, s.sortOrder
            order by count(v) desc, s.sortOrder asc, s.id asc
            """)
    List<SongVoteCount> countVotesBySong(@Param("concertId") Long concertId);
}
