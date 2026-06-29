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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static User registerEmail(
            String userId,
            String encodedPassword,
            String email,
            String nickname,
            Gender gender,
            AgeGroup ageGroup
    ) {
        User user = new User();

        user.authProvider = AuthProvider.EMAIL;
        user.userId = userId;
        user.password = encodedPassword;
        user.email = email;
        user.nickname = nickname;
        user.gender = gender;
        user.ageGroup = ageGroup;
        user.status = Status.ACTIVE;
        user.onboardingCompleted = true;

        return user;
    }

    public static User registerKakao(
            String providerId,
            String email,
            String nickname,
            Gender gender,
            AgeGroup ageGroup
    ) {
        User user = new User();

        user.authProvider = AuthProvider.KAKAO;
        user.providerId = providerId;
        user.email = email;
        user.nickname = nickname;
        user.gender = gender;
        user.ageGroup = ageGroup;
        user.status = Status.ACTIVE;
        user.onboardingCompleted = true;

        return user;
    }

    public void updateProfile(String nickname, String email, Gender gender, AgeGroup ageGroup) {
        this.nickname = nickname;
        this.email = email;
        this.gender = gender;
        this.ageGroup = ageGroup;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void withdraw() {
        this.status = Status.WITHDRAWN;
    }
}