package com.loop.loop_backend.Setlist.repository;

import com.loop.loop_backend.Setlist.domain.Setlist;
import com.loop.loop_backend.Setlist.domain.SetlistType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SetlistRepository extends JpaRepository<Setlist, Long> {

    Optional<Setlist> findByPerformanceIdAndType(Long performanceId, SetlistType type);

    // 정렬은 서비스에서 enum 순서로 한다(문자열 컬럼이라 DB 정렬은 알파벳순)
    List<Setlist> findByPerformanceId(Long performanceId);

    // 출연 정보 삭제·헤드라이너 해제 가드(SetlistDataGuard)
    boolean existsByPerformanceId(Long performanceId);

    boolean existsByPerformanceIdIn(Collection<Long> performanceIds);
}
