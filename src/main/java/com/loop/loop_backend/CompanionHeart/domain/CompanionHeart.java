package com.loop.loop_backend.CompanionHeart.domain;

import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
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

@Entity
@Table(name = "companion_hearts",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_companion_post", columnNames = {"user_id", "companion_post_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanionHeart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "companion_post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CompanionPost companionPost;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private CompanionHeart(User user, CompanionPost companionPost) {
        this.user = user;
        this.companionPost = companionPost;
    }
}