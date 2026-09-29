package com.loop.loop_backend.ConcertScrap.domain;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

// 유저가 콘서트를 스크랩한 기록. 한 유저가 같은 콘서트를 두 번 스크랩할 수 없도록 (user, concert)에 유니크 제약.
// 콘서트가 삭제되면 스크랩도 DB에서 함께 삭제된다(ON DELETE CASCADE). 회원 탈퇴 시에는 UserServiceImpl이 직접 지운다.
@Entity
@Table(name = "concert_scraps",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_concert_scrap", columnNames = {"user_id", "concert_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConcertScrap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Concert concert;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ConcertScrap(User user, Concert concert) {
        this.user = user;
        this.concert = concert;
    }
}