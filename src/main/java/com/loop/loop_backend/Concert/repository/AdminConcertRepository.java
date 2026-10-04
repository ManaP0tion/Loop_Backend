package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 공연 목록 전용 조회. ConcertRepository는 사용자용 조회와 쿼리 최적화 작업(팀원 담당) 영역이라 따로 둔다.
 */
public interface AdminConcertRepository extends JpaRepository<Concert, Long> {

    String SEARCH_CONDITION =
            "WHERE (:category IS NULL OR c.category = :category) " +
            "AND (:published IS NULL OR c.published = :published) " +
            "AND (:keyword IS NULL " +
            "  OR LOWER(c.title) LIKE :keyword " +
            "  OR LOWER(c.venue) LIKE :keyword " +            // 공연장이 아직 연결되지 않은 기존 공연의 KOPIS 장소 문자열
            "  OR LOWER(v.name) LIKE :keyword " +
            "  OR LOWER(a.name) LIKE :keyword " +
            "  OR LOWER(a.nameKo) LIKE :keyword " +
            "  OR LOWER(a.nameAlias) LIKE :keyword " +
            // 별칭은 여러 개라 조인하면 같은 공연이 여러 번 나오므로 존재 여부로만 본다
            "  OR EXISTS (SELECT 1 FROM Concert c2 JOIN c2.titleAliases alias " +
            "             WHERE c2.id = c.id AND LOWER(alias) LIKE :keyword))";

    /**
     * 등록된 공연 목록. 공연장·아티스트는 함께 가져와 행마다 추가 조회가 나가지 않게 한다(N+1 방지).
     * @param keyword 소문자로 만든 LIKE 패턴(예: "%yuuri%"). null이면 검색하지 않는다
     */
    @Query(value = "SELECT c FROM Concert c LEFT JOIN FETCH c.linkedVenue v LEFT JOIN FETCH c.artist a " + SEARCH_CONDITION,
            countQuery = "SELECT COUNT(c) FROM Concert c LEFT JOIN c.linkedVenue v LEFT JOIN c.artist a " + SEARCH_CONDITION)
    Page<Concert> search(@Param("keyword") String keyword,
                         @Param("category") ConcertCategory category,
                         @Param("published") Boolean published,
                         Pageable pageable);
}