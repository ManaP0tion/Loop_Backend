package com.loop.loop_backend.HashTag.domain;

import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_hashtags",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_tag", columnNames = {"user_id", "tag"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserHashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "tag", length = 5, nullable = false)
    private String tag;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private UserHashtag(User user, String tag) {
        this.user = user;
        this.tag = tag;
    }
}