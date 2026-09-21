package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConcertRepository extends JpaRepository<Concert, Long> {

    // 어드민 검색: 공연명/장소/아티스트명(별칭 포함). q 없으면 전체
    @Query("SELECT c FROM Concert c LEFT JOIN c.artist a WHERE :q IS NULL " +
            "OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(c.venue) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(a.baseName) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(a.nameKo) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(a.nameAlias) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Concert> searchForAdmin(@Param("q") String q, Pageable pageable);

    @Query("SELECT c FROM Concert c " +
            "WHERE c.artist.id = :artistId " +
            "AND (COALESCE(c.endDate, c.startDate) IS NULL OR COALESCE(c.endDate, c.startDate) >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> findUpcomingOrUndatedByArtistId(@Param("artistId") Long artistId,
                                                  @Param("date") LocalDate date);

    // 검색(section/period 스코프)용: 제목 또는 아티스트명(원어명/한글명/별칭)으로 매칭 + 카테고리 필터.
    // section 미지정(전체검색) 시 서비스가 ConcertCategory 전체 값을 넘겨서 카테고리 제한 없이 동작한다.
    @Query("SELECT c FROM Concert c LEFT JOIN c.artist a " +
            "WHERE (LOWER(c.title) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.name) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.baseName) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.nameKo) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.nameAlias) LIKE LOWER(CONCAT('%', :title, '%'))) " +
            "AND c.category IN :categories " +
            "AND (COALESCE(c.endDate, c.startDate) IS NULL OR COALESCE(c.endDate, c.startDate) >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> searchUpcomingOrUndatedByTitleAndCategories(@Param("title") String title,
                                                               @Param("categories") List<ConcertCategory> categories,
                                                               @Param("date") LocalDate date);

    @Query("SELECT c FROM Concert c LEFT JOIN c.artist a " +
            "WHERE (LOWER(c.title) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.name) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.baseName) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.nameKo) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR LOWER(a.nameAlias) LIKE LOWER(CONCAT('%', :title, '%'))) " +
            "AND c.category IN :categories " +
            "AND COALESCE(c.endDate, c.startDate) IS NOT NULL " +
            "AND COALESCE(c.endDate, c.startDate) < :date " +
            "ORDER BY COALESCE(c.endDate, c.startDate) DESC")
    List<Concert> searchPastByTitleAndCategories(@Param("title") String title,
                                                  @Param("categories") List<ConcertCategory> categories,
                                                  @Param("date") LocalDate date);

    // section(대분류) 조회용: 카테고리 여러 개를 한 번에 받는다. 예정 공연 - 임박순(가까운 날짜부터).
    @Query("SELECT c FROM Concert c " +
            "WHERE c.category IN :categories " +
            "AND (COALESCE(c.endDate, c.startDate) IS NULL OR COALESCE(c.endDate, c.startDate) >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> findUpcomingOrUndatedByCategories(@Param("categories") List<ConcertCategory> categories,
                                                     @Param("date") LocalDate date);

    // section(대분류) 조회용: 이미 종료된 공연만 - 최근 종료순. 날짜 미정 공연은 "지난 공연"이 아니므로 제외.
    @Query("SELECT c FROM Concert c " +
            "WHERE c.category IN :categories " +
            "AND COALESCE(c.endDate, c.startDate) IS NOT NULL " +
            "AND COALESCE(c.endDate, c.startDate) < :date " +
            "ORDER BY COALESCE(c.endDate, c.startDate) DESC")
    List<Concert> findPastByCategories(@Param("categories") List<ConcertCategory> categories,
                                       @Param("date") LocalDate date);

    List<Concert> findByArtistId(Long artistId);
    Optional<Concert> findByTitle(String title);
}
