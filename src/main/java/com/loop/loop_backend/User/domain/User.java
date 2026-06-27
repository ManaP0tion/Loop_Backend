package com.loop.loop_backend.User.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uq_provider", columnNames = {"auth_provider", "provider_id"}))
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 10)
    private AuthProvider authProvider;

    @Column(name = "user_id", length = 50, unique = true)
    private String userId;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false, length = 10)
    private AgeGroup ageGroup;

    @Enumerated(EnumType.STRING)
    @Column(name="status", nullable = false, length = 10)
    private Status status;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
