package com.loop.loop_backend.Report.domain;

import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @org.hibernate.annotations.ColumnDefault("'RECEIVED'")
    private ReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    @org.hibernate.annotations.ColumnDefault("'NONE'")
    private ReportAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "appeal_status", nullable = false, length = 20)
    @org.hibernate.annotations.ColumnDefault("'NONE'")
    private AppealStatus appealStatus;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    // 신고자·피신고자 채팅방 (신고 접수 시 스냅샷). 없으면 채팅 없는 신고.
    @Column(name = "chat_room_id")
    private Long chatRoomId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "report", fetch = FetchType.LAZY)
    private List<ReportImage> images = new ArrayList<>();

    @Builder
    private Report(User reporter, User targetUser, String reason, String detail, Long chatRoomId) {
        this.reporter = reporter;
        this.targetUser = targetUser;
        this.reason = reason;
        this.detail = detail;
        this.chatRoomId = chatRoomId;
        this.status = ReportStatus.RECEIVED;
        this.action = ReportAction.NONE;
        this.appealStatus = AppealStatus.NONE;
    }

    public void updateStatus(ReportStatus status) {
        this.status = status;
    }

    public void applyAction(ReportAction action, String adminNote) {
        this.action = action;
        this.adminNote = adminNote;
        this.status = ReportStatus.ACTION_TAKEN;
        this.processedAt = LocalDateTime.now();
    }

    public void closeWithoutAction(String adminNote) {
        this.status = ReportStatus.CLOSED;
        this.adminNote = adminNote;
        this.processedAt = LocalDateTime.now();
    }

    public void raiseAppeal() {
        this.appealStatus = AppealStatus.RAISED;
    }

    public void resolveAppeal(AppealStatus resolution, String adminNote) {
        this.appealStatus = resolution;
        if (adminNote != null) this.adminNote = adminNote;
    }
}