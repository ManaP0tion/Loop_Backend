package com.loop.loop_backend.User.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
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
@BatchSize(size = 20)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    @org.hibernate.annotations.ColumnDefault("'USER'")
    private Role role;

    // 정지 자동 해제 시각 (SUSPENDED 상태에서만 의미). null 이면 영구정지.
    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    // 추후 확장 대비
    @Column(name = "user_id", length = 50, unique = true)
    private String userId;
    // 추후 확장 대비
    @Column(name = "password", length = 255)
    private String password;
    // 이메일 인증 완료 시 저장됨 (고객센터 문의 답변 등에 사용)
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
    @Column(name = "blocked_user_id")
    private Long blockedUserId;

    @Column(name = "concert_reminder_email", nullable = false)
    private boolean concertReminderEmail = true;

    @Column(name = "chat_notification_email", nullable = false)
    private boolean chatNotificationEmail = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private User(AuthProvider authProvider, String providerId,Status status, boolean onboardingCompleted, Role role) {
        this.authProvider = authProvider;
        this.providerId = providerId;
        this.status = status != null ? status : Status.ACTIVE;
        this.role = role != null ? role : Role.USER;
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

    public void verifyEmail(String email) {
        this.email = email;
    }

    // 이메일 인증 완료 여부를 별도 플래그 없이 email 존재 여부로 판단 (verifyEmail()에서만 email이 세팅됨)
    public boolean isEmailVerified() {
        return email != null;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** dev 전용: 테스트 유저에 로그인 자격(userId/password) 주입. */
    public void assignEmailCredentials(String userId, String encodedPassword) {
        this.userId = userId;
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

    // 탈퇴 계정 재가입: 같은 계정을 살리되 온보딩부터 새로 하도록 초기화
    public void reactivate() {
        this.status = Status.ACTIVE;
        this.onboardingCompleted = false;
        this.nickname = null;
        this.birthDate = null;
        this.gender = null;
    }

    public void updateNotificationSettings(Boolean concertReminderEmail, Boolean chatNotificationEmail) {
        if (concertReminderEmail != null) this.concertReminderEmail = concertReminderEmail;
        if (chatNotificationEmail != null) this.chatNotificationEmail = chatNotificationEmail;
    }

    // 관리자 조작
    public void suspend(LocalDateTime until) {
        this.status = Status.SUSPENDED;
        this.suspendedUntil = until;   // null 이면 영구
    }

    public void liftSuspension() {
        this.status = Status.ACTIVE;
        this.suspendedUntil = null;
    }

    // suspendedUntil 지났으면 true. null(영구) 이면 false.
    public boolean isSuspensionExpired() {
        return status == Status.SUSPENDED && suspendedUntil != null && suspendedUntil.isBefore(LocalDateTime.now());
    }

    public void terminate() {
        this.status = Status.WITHDRAWN;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void updateBirthAndGender(java.time.LocalDate birthDate, Gender gender) {
        if (birthDate != null) this.birthDate = birthDate;
        if (gender != null) this.gender = gender;
    }
}