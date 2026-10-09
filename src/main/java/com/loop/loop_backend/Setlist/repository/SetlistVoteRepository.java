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

    // 적중률 전체 평균(NO.68): 투표자별로 고른 곡을 한 번에 가져온다
    @Query("""
            select new com.loop.loop_backend.Setlist.repository.VoteSongRow(v.id, s.id)
            from SetlistVote v join v.songs s
            where v.concert.id = :concertId
            """)
    List<VoteSongRow> findVoteSongs(@Param("concertId") Long concertId);

    /**
     * 결과 메일 대상(NO.69): 투표 + 수신 동의 + 이메일 인증(email 존재) + 설정의 셋리스트 결과 알림 ON.
     * 탈퇴 유저는 투표가 지워져 있어 걸리지 않는다.
     */
    @Query("""
            select new com.loop.loop_backend.Setlist.repository.SetlistResultRecipient(u.email, u.nickname)
            from SetlistVote v join v.user u
            where v.concert.id = :concertId
              and v.resultMailConsent = true
              and u.email is not null
              and u.setlistResultEmail = true
            """)
    List<SetlistResultRecipient> findResultMailRecipients(@Param("concertId") Long concertId);
}
