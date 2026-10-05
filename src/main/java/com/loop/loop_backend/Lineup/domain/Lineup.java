package com.loop.loop_backend.Lineup.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Concert.domain.Concert;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.temporal.ChronoUnit;

/**
 * 페스티벌 라인업 한 줄(AD-04). 페스티벌 페이지 라인업과 동행 '꼭 보고 싶은 무대'의 원본.
 * 이름·이미지는 Artist 값을 그대로 쓴다. 같은 아티스트가 여러 DAY에 나올 수 있어 (공연, 아티스트, DAY)로 유니크.
 * DAY는 날짜가 아니라 1부터 세는 번호 - 동행 WatchDay(ordinal + 1)와 그대로 비교하려고.
 * 공연 기간이 줄어 범위를 벗어난 DAY는 공연 수정 서비스가 지운다.
 */
@Entity
@Table(name = "lineups",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_lineup_concert_artist_day",
                columnNames = {"concert_id", "artist_id", "day_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lineup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Concert concert;

    // 아티스트를 지우면 라인업에서도 빠진다(Concert.artist와 같은 규칙)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artist artist;

    @Column(name = "day_no", nullable = false)
    private int day;

    // DAY당 개수 제한 없음
    @Column(name = "headliner", nullable = false)
    private boolean headliner;

    // 전체 탭 노출 순서. 새 항목은 맨 뒤
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // 규칙 위반은 IllegalArgumentException → GlobalExceptionHandler가 400(INVALID_INPUT)으로 응답한다(Concert와 동일).
    @Builder
    private Lineup(Concert concert, Artist artist, int day, int displayOrder) {
        if (!concert.isFestival()) {
            throw new IllegalArgumentException("라인업은 페스티벌에만 등록할 수 있다");
        }
        this.concert = concert;
        this.artist = artist;
        this.day = validDay(concert, day);
        this.displayOrder = displayOrder;
    }

    public void changeDay(int day) {
        this.day = validDay(concert, day);
    }

    public void changeHeadliner(boolean headliner) {
        this.headliner = headliner;
    }

    /** 순서 한 칸 이동: 인접 항목과 노출 순서를 맞바꾼다. */
    public void swapOrderWith(Lineup other) {
        int mine = this.displayOrder;
        this.displayOrder = other.displayOrder;
        other.displayOrder = mine;
    }

    /** 공연 일수. 기간 미정이면 0 - 이때는 어떤 DAY도 등록할 수 없다. */
    public static int dayCount(Concert concert) {
        if (concert.getStartDate() == null || concert.getEndDate() == null) return 0;
        return (int) ChronoUnit.DAYS.between(concert.getStartDate(), concert.getEndDate()) + 1;
    }

    private static int validDay(Concert concert, int day) {
        int days = dayCount(concert);
        if (days == 0) {
            throw new IllegalArgumentException("공연 기간 없이 라인업 DAY를 정할 수 없다");
        }
        if (day < 1 || day > days) {
            throw new IllegalArgumentException("DAY는 1~" + days + " 사이: " + day);
        }
        return day;
    }
}
