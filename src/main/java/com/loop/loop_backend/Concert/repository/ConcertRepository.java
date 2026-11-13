package com.loop.loop_backend.Concert.repository;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConcertRepository extends JpaRepository<Concert, Long> {

    // 동행 모집 공연 목록의 조건. 사용자에게 보이는 동행 상태(Concert.companionStatusForUsers)를 SQL로 옮긴 것이라 그 규칙이 바뀌면 함께 바꾼다.
    // - 사용자에게 모집 중 = 공개 + 저장 상태 OPEN
    // - 사용자에게 마감 = 저장 상태 CLOSED, 또는 비공개 + 저장 상태 OPEN
    // - 미오픈(NOT_OPENED)은 공개 여부와 상관없이 미오픈
    // 지난 공연 제외(날짜 미정은 포함)는 공연 탭 예정 목록과 같다.
    String NOT_PAST_CONDITION =
            "(COALESCE(c.endDate, c.startDate) IS NULL OR COALESCE(c.endDate, c.startDate) >= :date)";
    String COMPANION_OPEN_FOR_USERS = "(c.published = true " +
            "AND c.companionStatus = com.loop.loop_backend.Concert.domain.CompanionStatus.OPEN)";

    // 동행 모집 공연 목록: 미오픈 제외. 모집 중이 위, 마감이 아래이고 각각 공연일 가까운 순(날짜 미정은 맨 뒤), 같은 날이면 id 순.
    // pageable 크기만큼(미리보기)
    @Query("SELECT c FROM Concert c WHERE " +
            "c.companionStatus <> com.loop.loop_backend.Concert.domain.CompanionStatus.NOT_OPENED " +
            "AND " + NOT_PAST_CONDITION +
            " ORDER BY CASE WHEN " + COMPANION_OPEN_FOR_USERS + " THEN 0 ELSE 1 END, " +
            "CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC, c.id ASC")
    List<Concert> findCompanionConcerts(@Param("date") LocalDate date, Pageable pageable);

    // 위 목록 중 사용자에게 모집 중인 공연 수(마감 제외)
    @Query("SELECT COUNT(c) FROM Concert c WHERE " + COMPANION_OPEN_FOR_USERS + " AND " + NOT_PAST_CONDITION)
    long countCompanionOpen(@Param("date") LocalDate date);

    // KOPIS 직접 등록: 아티스트와 상관없이 이 KOPIS 공연으로 만든 공연이 있는지 / 검색 결과의 등록 상태 계산
    boolean existsByKopisId(String kopisId);
    List<Concert> findByKopisIdIn(Collection<String> kopisIds);

    // 어드민 검색: 공연명/장소/아티스트명(별칭 포함). q 없으면 전체
    @Query("SELECT c FROM Concert c LEFT JOIN c.artist a WHERE :q IS NULL " +
            "OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(c.venue) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(a.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(a.baseName) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(a.nameKo) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(a.nameAlias) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Concert> searchForAdmin(@Param("q") String q, Pageable pageable);

    @Query("SELECT c FROM Concert c LEFT JOIN FETCH c.artist LEFT JOIN FETCH c.linkedVenue " +
            "WHERE c.artist.id = :artistId " +
            "AND (COALESCE(c.endDate, c.startDate) IS NULL OR COALESCE(c.endDate, c.startDate) >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> findUpcomingOrUndatedByArtistId(@Param("artistId") Long artistId,
                                                  @Param("date") LocalDate date);

    // 검색(section/period 스코프)용: 제목, 공연명 별칭(어드민 입력), 아티스트명(원어명/한글명/별칭)으로 매칭 + 카테고리 필터.
    // section 미지정(전체검색) 시 서비스가 ConcertCategory 전체 값을 넘겨서 카테고리 제한 없이 동작한다.
    // 공연명 별칭은 여러 개라 조인하면 같은 공연이 여러 번 나오므로 EXISTS로 존재 여부만 본다.
    @Query("SELECT c FROM Concert c LEFT JOIN FETCH c.artist a LEFT JOIN FETCH c.linkedVenue " +
            "WHERE (LOWER(c.title) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR EXISTS (SELECT 1 FROM Concert c2 JOIN c2.titleAliases alias " +
            "              WHERE c2.id = c.id AND LOWER(alias) LIKE LOWER(CONCAT('%', :title, '%'))) " +
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

    @Query("SELECT c FROM Concert c LEFT JOIN FETCH c.artist a LEFT JOIN FETCH c.linkedVenue " +
            "WHERE (LOWER(c.title) LIKE LOWER(CONCAT('%', :title, '%')) " +
            "   OR EXISTS (SELECT 1 FROM Concert c2 JOIN c2.titleAliases alias " +
            "              WHERE c2.id = c.id AND LOWER(alias) LIKE LOWER(CONCAT('%', :title, '%'))) " +
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
    @Query("SELECT c FROM Concert c LEFT JOIN FETCH c.artist LEFT JOIN FETCH c.linkedVenue " +
            "WHERE c.category IN :categories " +
            "AND (COALESCE(c.endDate, c.startDate) IS NULL OR COALESCE(c.endDate, c.startDate) >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> findUpcomingOrUndatedByCategories(@Param("categories") List<ConcertCategory> categories,
                                                     @Param("date") LocalDate date,
                                                     Pageable pageable);

    default List<Concert> findUpcomingOrUndatedByCategories(List<ConcertCategory> categories, LocalDate date) {
        return findUpcomingOrUndatedByCategories(categories, date, Pageable.unpaged());
    }

    // 지난 공연 상세의 "다가오는 공연" 미리보기용: 위와 같은 조건·정렬에서 공개 공연만, pageable 크기만큼.
    @Query("SELECT c FROM Concert c LEFT JOIN FETCH c.artist LEFT JOIN FETCH c.linkedVenue " +
            "WHERE c.category IN :categories AND c.published = true " +
            "AND (COALESCE(c.endDate, c.startDate) IS NULL OR COALESCE(c.endDate, c.startDate) >= :date) " +
            "ORDER BY CASE WHEN c.startDate IS NULL THEN 1 ELSE 0 END, c.startDate ASC")
    List<Concert> findPublishedUpcomingOrUndatedByCategories(@Param("categories") List<ConcertCategory> categories,
                                                              @Param("date") LocalDate date,
                                                              Pageable pageable);

    // section(대분류) 조회용: 이미 종료된 공연만 - 최근 종료순. 날짜 미정 공연은 "지난 공연"이 아니므로 제외.
    @Query("SELECT c FROM Concert c LEFT JOIN FETCH c.artist LEFT JOIN FETCH c.linkedVenue " +
            "WHERE c.category IN :categories " +
            "AND COALESCE(c.endDate, c.startDate) IS NOT NULL " +
            "AND COALESCE(c.endDate, c.startDate) < :date " +
            "ORDER BY COALESCE(c.endDate, c.startDate) DESC")
    List<Concert> findPastByCategories(@Param("categories") List<ConcertCategory> categories,
                                       @Param("date") LocalDate date,
                                       Pageable pageable);

    default List<Concert> findPastByCategories(List<ConcertCategory> categories, LocalDate date) {
        return findPastByCategories(categories, date, Pageable.unpaged());
    }

    List<Concert> findByArtistId(Long artistId);
    Optional<Concert> findByTitle(String title);

    // 새 공연 알림(NO.47) 대상: 지금 공개 상태 + 마지막 공개 시각이 [publishedFrom, publishedTo] + 아직 알리지 않음 + 끝나지 않음.
    // 공개 상태면 종료일이 반드시 있다(Concert.validateState). 메일에 공연장 이름을 쓰므로 함께 가져온다.
    @Query("SELECT c FROM Concert c LEFT JOIN FETCH c.linkedVenue " +
            "WHERE c.published = true " +
            "AND c.publishedAt >= :publishedFrom AND c.publishedAt <= :publishedTo " +
            "AND c.newConcertNotifiedAt IS NULL " +
            "AND c.endDate >= :today " +
            "ORDER BY c.startDate ASC, c.id ASC")
    List<Concert> findNewConcertNotificationTargets(@Param("publishedFrom") LocalDateTime publishedFrom,
                                                    @Param("publishedTo") LocalDateTime publishedTo,
                                                    @Param("today") LocalDate today);
}
