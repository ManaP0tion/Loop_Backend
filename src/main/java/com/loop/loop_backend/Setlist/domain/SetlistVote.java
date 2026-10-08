package com.loop.loop_backend.Setlist.domain;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Song.domain.Song;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 유저의 예상 셋리스트 투표(NO.65). 공연당 유저 1건, 수정은 곡 선택을 통째로 교체한다.
 * 곡 수(1~n)·마감·후보 소속 검증은 서비스가 한다 - n과 마감 시각이 공연·서버 시각에 달려 있어서.
 */
@Entity
@Table(name = "setlist_votes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_setlist_vote_concert_user",
                columnNames = {"concert_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SetlistVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Concert concert;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToMany
    @JoinTable(name = "setlist_vote_songs",
            joinColumns = @JoinColumn(name = "vote_id"),
            inverseJoinColumns = @JoinColumn(name = "song_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<Song> songs = new HashSet<>();

    // 결과 메일 수신 동의(MD03). 수정 후 재제출해도 유지된다
    @Column(name = "result_mail_consent", nullable = false)
    private boolean resultMailConsent;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private SetlistVote(Concert concert, User user, Set<Song> songs) {
        this.concert = concert;
        this.user = user;
        replaceSongs(songs);
    }

    public void replaceSongs(Set<Song> songs) {
        this.songs.clear();
        this.songs.addAll(songs);
    }

    public void agreeResultMail() {
        this.resultMailConsent = true;
    }
}
