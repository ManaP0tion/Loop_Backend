package com.loop.loop_backend.CompanionPost.domain;

import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "companion_posts",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_concert_watch_day",
                columnNames = {"user_id", "concert_id", "watch_day"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "concert_id", nullable = false)
    private Long concertId; // Concert 엔티티 머지 전까지 단순 컬럼, 이후 연관관계로 전환

    @Enumerated(EnumType.STRING)
    @Column(name = "watch_day", nullable = false)
    private WatchDay watchDay;

    //함께 하고 싶은 것, 다중선택 별도 테이블
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "companion_post_activities",
            joinColumns = @JoinColumn(name = "companion_post_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "activity")
    @NotNull
    private Set<CompanionActivity> activities = new HashSet<>();

    // 함께하고 싶은 것에 공연 관람(CONCERT)을 선택했을 때만 의미 있는 값 - 그 외엔 null 허용
    @Enumerated(EnumType.STRING)
    @Column(name = "watch_style")
    private WatchStyle watchStyle;

    @Column(name = "message_to_companion", length = 200)
    @Size(max = 200)
    private String messageToCompanion;

    @Column(name = "visible", nullable = false)
    private boolean visible;

    @Column(name = "same_gender_only", nullable = false)
    private boolean sameGenderOnly;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private CompanionPost(User user, Long concertId, WatchDay watchDay,
                          Set<CompanionActivity> activities,
                          WatchStyle watchStyle, String messageToCompanion, boolean sameGenderOnly){
        this.user = user;
        this.concertId = concertId;
        this.watchDay = watchDay;
        this.activities = activities;
        this.watchStyle = watchStyle;
        this.messageToCompanion = messageToCompanion;
        this.visible = true;
        this.sameGenderOnly = sameGenderOnly;
    }


    public void toggleVisible(boolean visible){
        this.visible = visible;
    }

    public void update(Set<CompanionActivity> activities, WatchStyle watchStyle, String messageToCompanion,
                        boolean sameGenderOnly) {
        this.activities = activities;
        this.watchStyle = watchStyle;
        this.messageToCompanion = messageToCompanion;
        this.sameGenderOnly = sameGenderOnly;
    }


}
