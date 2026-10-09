package com.loop.loop_backend.Setlist.domain;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Song.domain.Song;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 공연의 셋리스트(AD-07). 지난 셋리스트(RECENT·PREVIOUS_VISIT)와 실제 셋리스트(ACTUAL)를 같은 모양으로 저장한다 - 입력 방식이 같아서.
 * (공연, 구분) 유니크라 지난 셋리스트는 최대 2건, 실제는 1건이 DB에서 보장된다.
 * 곡은 Song을 참조만 한다(표기를 복사하지 않음). 같은 곡 중복(앵코르 재연주)도 그대로 담는다.
 */
@Entity
@Table(name = "setlists",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_setlist_concert_type",
                columnNames = {"concert_id", "type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Setlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Concert concert;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private SetlistType type;

    // 이하 3개는 지난 셋리스트 헤더. 실제 셋리스트는 공연 값을 쓰므로 비워둔다
    @Column(name = "tour_name", length = 200)
    private String tourName;

    @Column(name = "performed_on")
    private LocalDate performedOn;

    @Column(name = "venue_name", length = 200)
    private String venueName;

    // 실제 셋리스트 결과 메일은 최초 저장 시 1회만(NO.69)
    @Column(name = "result_mail_sent_at")
    private LocalDateTime resultMailSentAt;

    @ManyToMany
    @JoinTable(name = "setlist_songs",
            joinColumns = @JoinColumn(name = "setlist_id"),
            inverseJoinColumns = @JoinColumn(name = "song_id"))
    @OrderColumn(name = "position")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<Song> songs = new ArrayList<>();

    // 규칙 위반은 IllegalArgumentException → 400(INVALID_INPUT), Lineup과 동일
    @Builder
    private Setlist(Concert concert, SetlistType type, String tourName, LocalDate performedOn,
                    String venueName, List<Song> songs) {
        if (concert.isFestival()) {
            throw new IllegalArgumentException("셋리스트는 단독 공연에만 등록할 수 있다");
        }
        this.concert = concert;
        this.type = type;
        changeHeader(tourName, performedOn, venueName);
        replaceSongs(songs);
    }

    public void changeHeader(String tourName, LocalDate performedOn, String venueName) {
        if (type.isPast() && (performedOn == null || venueName == null || venueName.isBlank())) {
            throw new IllegalArgumentException("지난 셋리스트는 날짜·장소가 필수다");
        }
        this.tourName = type.isPast() ? tourName : null;
        this.performedOn = type.isPast() ? performedOn : null;
        this.venueName = type.isPast() ? venueName : null;
    }

    /** 곡 목록 통째 교체. 순서가 곧 공연 순서. */
    public void replaceSongs(List<Song> songs) {
        if (songs == null || songs.isEmpty()) {
            throw new IllegalArgumentException("셋리스트에는 곡이 1곡 이상 있어야 한다");
        }
        this.songs.clear();
        this.songs.addAll(songs);
    }

    /** 결과 메일을 보낼 차례면 true를 돌려주고 발송 시각을 남긴다. 실제 셋리스트 최초 저장에만 true. */
    public boolean markResultMailSent(LocalDateTime now) {
        if (type != SetlistType.ACTUAL || resultMailSentAt != null) return false;
        this.resultMailSentAt = now;
        return true;
    }
}
