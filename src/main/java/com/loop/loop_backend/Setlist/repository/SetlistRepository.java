package com.loop.loop_backend.Setlist.repository;

import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SetlistRepository extends JpaRepository<Setlist, Long> {

    Optional<Setlist> findByPerformanceIdAndType(Long performanceId, SetlistType type);

    // 정렬은 서비스에서 enum 순서로 한다(문자열 컬럼이라 DB 정렬은 알파벳순)
    List<Setlist> findByPerformanceId(Long performanceId);

    // 출연 정보 삭제·헤드라이너 해제 가드(SetlistDataGuard)
    boolean existsByPerformanceId(Long performanceId);

    boolean existsByPerformanceIdIn(Collection<Long> performanceIds);

    /**
     * 이전 실제 셋리스트 불러오기(F-1): 같은 아티스트의 다른 출연 정보에 저장된 실제 셋리스트, 공연일 최신순.
     * 단독 공연·페스티벌 헤드라이너 모두, 비공개 공연도 포함한다(관리자 화면용).
     */
    @Query("""
            select s from Setlist s
            join fetch s.performance p join fetch p.concert c left join fetch c.linkedVenue
            where s.type = com.loop.loop_backend.Setlist.domain.SetlistType.ACTUAL
              and p.artist.id = :artistId and p.id <> :excludePerformanceId
            order by c.startDate desc nulls last, s.id desc
            """)
    List<Setlist> findActualsByArtist(@Param("artistId") Long artistId,
                                      @Param("excludePerformanceId") Long excludePerformanceId, Pageable pageable);

    // 셋리스트 곡 목록(공연 순서). Song의 @SQLRestriction을 거치지 않으려고 네이티브로 읽는다 - 삭제된 곡도 셋리스트에는 남는다(F-2)
    @Query(value = """
            select ss.setlist_id, s.id, s.title_original, s.title_ko, s.album_art_url,
                   case when s.deleted_at is null then 0 else 1 end
            from setlist_songs ss join song s on s.id = ss.song_id
            where ss.setlist_id in (:setlistIds)
            order by ss.setlist_id, ss.position
            """, nativeQuery = true)
    List<Object[]> findSongRowsIncludingDeleted(@Param("setlistIds") Collection<Long> setlistIds);

    /** 셋리스트 id → 곡 목록(공연 순서, 삭제된 곡 포함). */
    default Map<Long, List<SetlistSong>> findSongsIncludingDeleted(Collection<Long> setlistIds) {
        Map<Long, List<SetlistSong>> result = new HashMap<>();
        if (setlistIds.isEmpty()) return result;
        for (Object[] row : findSongRowsIncludingDeleted(setlistIds)) {
            result.computeIfAbsent(((Number) row[0]).longValue(), k -> new ArrayList<>()).add(SetlistSong.fromRow(row, 1));
        }
        return result;
    }

    // 공연 후 결과의 미출현 곡 표기 - 투표한 곡이 나중에 삭제돼도 이름을 보여 준다
    @Query(value = """
            select s.id, s.title_original, s.title_ko, s.album_art_url,
                   case when s.deleted_at is null then 0 else 1 end
            from song s
            where s.id in (:songIds)
            """, nativeQuery = true)
    List<Object[]> findSongRowsByIdIncludingDeleted(@Param("songIds") Collection<Long> songIds);

    /** 곡 id → 곡 표기(삭제된 곡 포함). */
    default Map<Long, SetlistSong> findSongsByIdIncludingDeleted(Collection<Long> songIds) {
        Map<Long, SetlistSong> result = new HashMap<>();
        if (songIds.isEmpty()) return result;
        for (Object[] row : findSongRowsByIdIncludingDeleted(songIds)) {
            SetlistSong song = SetlistSong.fromRow(row, 0);
            result.put(song.songId(), song);
        }
        return result;
    }
}
