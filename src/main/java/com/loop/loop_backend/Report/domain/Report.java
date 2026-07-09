package com.loop.loop_backend.Report.domain;

import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports",
        uniqueConstraints = @UniqueConstraint(name = "uq_reporter_target", columnNames = {"reporter_id", "target_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Report(User reporter, User targetUser, String reason, String detail) {
        this.reporter = reporter;
        this.targetUser = targetUser;
        this.reason = reason;
        this.detail = detail;
    }
}