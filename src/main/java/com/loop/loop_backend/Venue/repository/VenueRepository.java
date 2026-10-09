package com.loop.loop_backend.Venue.repository;

import com.loop.loop_backend.Venue.domain.Venue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    Page<Venue> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

    // hallId가 null이면 "홀 ID가 비어 있는 공연장"을 찾는다 (파생 쿼리는 null 인자를 IS NULL로 바꾼다).
    Optional<Venue> findByKopisFacilityIdAndKopisHallId(String kopisFacilityId, String kopisHallId);

    // 공연장에 연결된 공연 수. 공연을 세지만 공연장 관리 화면에서만 쓰는 값이라 여기에 둔다.
    @Query("SELECT COUNT(c) FROM Concert c WHERE c.linkedVenue.id = :venueId")
    long countConcerts(@Param("venueId") Long venueId);

    // 목록 한 페이지의 공연장들을 한 번에 센다 (공연장마다 세면 N+1). 연결된 공연이 없는 공연장은 결과에 없다.
    @Query("SELECT c.linkedVenue.id AS venueId, COUNT(c) AS concertCount FROM Concert c " +
            "WHERE c.linkedVenue.id IN :venueIds GROUP BY c.linkedVenue.id")
    List<VenueConcertCount> countConcertsByVenueIds(@Param("venueIds") Collection<Long> venueIds);

    interface VenueConcertCount {
        Long getVenueId();
        Long getConcertCount();
    }
}