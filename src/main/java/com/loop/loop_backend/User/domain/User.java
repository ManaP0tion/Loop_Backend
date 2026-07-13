package com.loop.loop_backend.User.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;

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

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    // 추후 확장 대비
    @Column(name = "user_id", length = 50, unique = true)
    private String userId;
    // 추후 확장 대비
    @Column(name = "password", length = 255)
    private String password;
    // 추후 확장 대비
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Size(max = 5, message = "닉네임은 5자 이하여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임에 특수문자를 사용할 수 없습니다.")
    @Column(name = "nickname", length = 50, unique = true)
    private String nickname;

    @Past(message = "생년월일은 과거 날짜여야 합니다.")
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    // 연관관계 연결 전
    @Column(name = "artist_id")
    private Long artistId;
    // 연관관계 연결 전
    @Column(name = "blocked_user_id")
    private Long blockedUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private User(AuthProvider authProvider, String providerId,Status status, boolean onboardingCompleted) {
        this.authProvider = authProvider;
        this.providerId = providerId;
        this.status = status != null ? status : Status.ACTIVE;
        this.onboardingCompleted = onboardingCompleted;
    }

    public void updateUserProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void completeOnboarding(String nickname, LocalDate birthDate, Gender gender) {
        this.nickname = nickname;
        this.birthDate = birthDate;
        this.gender = gender;
        this.onboardingCompleted = true;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public AgeGroup getAgeGroup() {
        return AgeGroup.from(this.birthDate);
    }

    public Integer getAge() {
        return this.birthDate != null
                ? Period.between(this.birthDate, LocalDate.now()).getYears()
                : null;
    }

    public void withdraw() {
        this.status = Status.WITHDRAWN;
    }
}