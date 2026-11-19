package com.loop.loop_backend.Song.Repository;

import com.loop.loop_backend.Song.domain.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {

    List<Song> findAllByArtistIdOrderBySortOrderAsc(Long artistId);

    @Query("select coalesce(max(s.sortOrder), 0) from Song s where s.artist.id = :artistId")
    int findMaxSortOrder(@Param("artistId") Long artistId);

    // @SQLRestriction을 우회해야 해서 네이티브. 재불러오기 때 소프트 삭제 곡을 되살리지 않고 skip하는 판정용
    @Query(value = "SELECT track_id FROM song WHERE artist_id = :artistId " +
            "AND deleted_at IS NOT NULL AND track_id IS NOT NULL", nativeQuery = true)
    List<Long> findDeletedTrackIds(@Param("artistId") Long artistId);

    // 아티스트 삭제 전용. 소프트 삭제된 곡도 artist_id FK를 잡고 있어 @SQLDelete를 우회해 물리 삭제한다
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM song WHERE artist_id = :artistId", nativeQuery = true)
    int hardDeleteAllByArtistId(@Param("artistId") Long artistId);
}
