package com.loop.loop_backend.Setlist.repository;

import com.loop.loop_backend.Setlist.domain.SetlistVote;
import com.loop.loop_backend.User.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SetlistVoteRepository extends JpaRepository<SetlistVote, Long> {

    Optional<SetlistVote> findByPerformanceIdAndUserId(Long performanceId, Long userId);

    long countByPerformanceId(Long performanceId);

    // 출연 정보 삭제·헤드라이너 해제 가드(SetlistDataGuard)
    boolean existsByPerformanceId(Long performanceId);

    boolean existsByPerformanceIdIn(Collection<Long> performanceIds);

    // 회원탈퇴 정리용(UserServiceImpl.withdrawUser) - 탈퇴는 users 행을 지우지 않아 FK cascade가 동작하지 않는다
    void deleteAllByUser(User user);

    /**
     * 곡별 득표 수(NO.66). 득표순, 동점이면 곡 정렬 순번 순 - 공동 순위 없이 하이라이트 경계가 호출마다 같게.
     * 소프트 삭제된 곡은 Song의 @SQLRestriction으로 빠진다.
     */
    @Query("""
            select new com.loop.loop_backend.Setlist.repository.SongVoteCount(s.id, count(v))
            from SetlistVote v join v.songs s
            where v.performance.id = :performanceId
            group by s.id, s.sortOrder
            order by count(v) desc, s.sortOrder asc, s.id asc
            """)
    List<SongVoteCount> countVotesBySong(@Param("performanceId") Long performanceId);

    /**
     * 공연 후 결과(NO.60~62, 68)용 곡별 득표 수. countVotesBySong과 같은 정렬이지만 삭제된 곡도 센다 -
     * 투표한 곡이 공연 뒤 삭제돼도 적중률·팬 예상 상위 n곡이 바뀌지 않게(F-2). Song의 @SQLRestriction을 피하려고 네이티브.
     */
    @Query(value = """
            select vs.song_id, count(*)
            from setlist_vote_songs vs
            join setlist_votes v on v.id = vs.vote_id
            join song s on s.id = vs.song_id
            where v.performance_id = :performanceId
            group by vs.song_id, s.sort_order
            order by count(*) desc, s.sort_order asc, vs.song_id asc
            """, nativeQuery = true)
    List<Object[]> countVoteRowsIncludingDeleted(@Param("performanceId") Long performanceId);

    default List<SongVoteCount> countVotesBySongIncludingDeleted(Long performanceId) {
        return countVoteRowsIncludingDeleted(performanceId).stream()
                .map(r -> new SongVoteCount(((Number) r[0]).longValue(), ((Number) r[1]).longValue()))
                .toList();
    }

    // 적중률 전체 평균(NO.68): 투표자별로 고른 곡을 한 번에 가져온다. 삭제된 곡도 포함(위와 같은 이유)
    @Query(value = """
            select vs.vote_id, vs.song_id
            from setlist_vote_songs vs join setlist_votes v on v.id = vs.vote_id
            where v.performance_id = :performanceId
            """, nativeQuery = true)
    List<Object[]> findVoteSongRowsIncludingDeleted(@Param("performanceId") Long performanceId);

    default List<VoteSongRow> findVoteSongsIncludingDeleted(Long performanceId) {
        return findVoteSongRowsIncludingDeleted(performanceId).stream()
                .map(r -> new VoteSongRow(((Number) r[0]).longValue(), ((Number) r[1]).longValue()))
                .toList();
    }

    // 공연 단위 내 적중률: 출연 정보·유저별로 고른 곡. 삭제된 곡도 포함(위와 같은 이유)
    @Query(value = """
            select v.performance_id, v.user_id, vs.song_id
            from setlist_vote_songs vs join setlist_votes v on v.id = vs.vote_id
            where v.performance_id in (:performanceIds)
            """, nativeQuery = true)
    List<Object[]> findUserSongRowsIncludingDeleted(@Param("performanceIds") Collection<Long> performanceIds);

    /**
     * 결과 메일 대상(NO.69): 투표 + 수신 동의 + 이메일 인증(email 존재) + 설정의 셋리스트 결과 알림 ON.
     * 탈퇴 유저는 투표가 지워져 있어 걸리지 않는다.
     */
    @Query("""
            select new com.loop.loop_backend.Setlist.repository.SetlistResultRecipient(u.email, u.nickname)
            from SetlistVote v join v.user u
            where v.performance.id = :performanceId
              and v.resultMailConsent = true
              and u.email is not null
              and u.setlistResultEmail = true
            """)
    List<SetlistResultRecipient> findResultMailRecipients(@Param("performanceId") Long performanceId);
}
