package com.loop.loop_backend.Admin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 개인정보 보호법 「안전성 확보조치 기준」 제8조 + 처리방침 제10조 5항.
 * 운영자 개인정보 접근 기록 1년 보관 + 월 1회 점검.
 */
@Entity
@Table(name = "admin_access_logs", indexes = {
        @Index(name = "idx_aal_admin_created", columnList = "admin_id, created_at"),
        @Index(name = "idx_aal_target", columnList = "target_type, target_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "ip", length = 45)
    private String ip;

    // VIEW_USER / UPDATE_USER / SUSPEND_USER / VIEW_REPORT_CHAT / ...
    @Column(name = "action", nullable = false, length = 40)
    private String action;

    // USER / REPORT / CHAT_ROOM / INQUIRY / PROFILE / ...
    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "description", length = 500)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private AdminAccessLog(Long adminId, String ip, String action, String targetType, Long targetId, String description) {
        this.adminId = adminId;
        this.ip = ip;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.description = description;
    }
}
