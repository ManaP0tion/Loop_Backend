package com.loop.loop_backend.CompanionPost.domain;

import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "companion_posts")
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

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_gender")
    private PreferredGender preferredGender;

    //선호하는 동행자 나이대, 다중선택 별도 테이블
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "companion_post_preferred_age_groups",
            joinColumns = @JoinColumn(name = "companion_post_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_age_group")
    @NotNull
    private Set<PreferredAgeGroup> preferredAgeGroups = new HashSet<>();

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

    @Enumerated(EnumType.STRING)
    @Column(name = "watch_style", nullable = false)
    private WatchStyle watchStyle;

    @Column(name = "message_to_companion", length = 200)
    private String messageToCompanion;

    @Column(name = "visible", nullable = false)
    private boolean visible;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private CompanionPost(User user, Long concertId, WatchDay watchDay, PreferredGender preferredGender,
                          Set<PreferredAgeGroup> preferredAgeGroups, Set<CompanionActivity> activities,
                          WatchStyle watchStyle, String messageToCompanion){
        this.user = user;
        this.concertId = concertId;
        this.watchDay = watchDay;
        this.preferredGender = preferredGender;
        this.preferredAgeGroups = preferredAgeGroups;
        this.activities = activities;
        this.watchStyle = watchStyle;
        this.messageToCompanion = messageToCompanion;
        this.visible = true;
    }


    public void toggleVisible(boolean visible){
        this.visible = visible;
    }


}
