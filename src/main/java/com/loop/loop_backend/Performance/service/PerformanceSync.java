package com.loop.loop_backend.Performance.service;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Performance.domain.Performance;
import com.loop.loop_backend.Performance.repository.PerformanceRepository;
import com.loop.loop_backend.Setlist.service.SetlistDataGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 공연을 저장·수정한 뒤 출연 정보를 공연에 맞춘다. Concert는 출연 정보를 모르므로 공연을 바꾸는 모든 경로가 이걸 부른다
 * (관리자 공연 등록·수정, 예전 공연 등록·수정 API, KOPIS 승인). 바뀐 게 없으면 지워지거나 바뀌는 행이 없다.
 * - 유형이 바뀌면 기존 출연 정보를 모두 지운다(페스티벌 라인업과 단독 공연 출연 정보는 서로 다른 데이터).
 * - 페스티벌: 기간이 줄면 범위를 벗어난 출연 DAY를 빼고, DAY가 하나도 남지 않은 출연 정보는 지운다. 예상 곡 수는 라인업에서 헤드라이너별로 정한다.
 * - 단독 공연: Concert.artist에 맞춰 출연 정보 1건을 만들거나 아티스트를 바꾸거나 지운다. 예상 곡 수는 여기에 둔다.
 * 셋리스트·투표가 붙은 출연 정보를 지우게 되면 409 - 호출한 쪽 트랜잭션이 롤백되어 공연 변경도 반영되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PerformanceSync {

    private final PerformanceRepository performanceRepository;
    private final SetlistDataGuard setlistDataGuard;

    /** expectedSongCount: 단독 공연 예상 곡 수. null이면 변경 없음. */
    public void sync(Concert concert, boolean categoryChanged, Integer expectedSongCount) {
        Long concertId = concert.getId();
        if (concert.isFestival()) {
            if (expectedSongCount != null) {
                throw new IllegalArgumentException("페스티벌 예상 곡 수는 라인업에서 헤드라이너별로 정한다");
            }
            dropDaysAfter(concertId, categoryChanged ? 0 : Performance.dayCount(concert));
            return;
        }
        if (categoryChanged || concert.getArtist() == null) {
            dropDaysAfter(concertId, 0);
        }
        if (concert.getArtist() == null) {
            if (expectedSongCount != null) {
                throw new IllegalArgumentException("아티스트 없이 예상 곡 수를 정할 수 없다");
            }
            return;
        }
        // 아티스트를 바꿔도 출연 정보는 그대로 두고 아티스트만 바꾼다 - 이미 받은 투표·셋리스트는 남는다(기존 동작 유지)
        Performance solo = performanceRepository.findFirstByConcertId(concertId)
                .orElseGet(() -> performanceRepository.save(Performance.solo(concert, concert.getArtist())));
        solo.changeArtist(concert.getArtist());
        if (expectedSongCount != null) solo.changeExpectedSongCount(expectedSongCount);
    }

    /**
     * 출연 DAY 중 dayCount를 넘는 것을 빼고(0이면 전부), DAY가 하나도 남지 않은 출연 정보는 지운다.
     * 지워질 출연 정보에 셋리스트·투표가 있으면 409 - 아무것도 지우기 전에 검사한다.
     */
    private void dropDaysAfter(Long concertId, int dayCount) {
        List<Performance> emptied = new ArrayList<>();
        for (Performance performance : performanceRepository.findByConcertId(concertId)) {
            if (performance.dropDaysAfter(dayCount)) emptied.add(performance);
        }
        setlistDataGuard.requireNone(emptied.stream().map(Performance::getId).toList());
        performanceRepository.deleteAll(emptied);
        // 다음 조회(단독 공연 출연 정보 찾기)가 지운 행을 다시 보지 않게 바로 반영한다
        performanceRepository.flush();
    }
}
