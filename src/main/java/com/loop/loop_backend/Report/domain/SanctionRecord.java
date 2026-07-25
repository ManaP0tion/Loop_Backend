package com.loop.loop_backend.Report.domain;

import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 실제로 적용된 정지 조치의 이력. 신고 처리(Report.applyAction)를 거쳐 정지됐든, 관리자가 신고 없이
// 직접 정지시켰든 이 테이블 한 곳에 모아 기록한다 - 사용자가 "내 제재 이력"을 조회할 땐 이 테이블만 보면 됨.
// Report는 (reporter, target) 유니크 제약이 있어 같은 사람을 반복해서 직접 정지시킨 기록을 못 남기고,
// 신고 목록/처리 화면 semantics와도 섞이면 안 되므로 Report와 완전히 분리된 테이블로 둔다.
@Entity
@Table(name = "sanction_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SanctionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    // 정지 해제 예정일시. null이면 영구정지 (User.suspendedUntil과 같은 의미)
    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    // 신고 처리 결과로 정지된 경우 그 Report를 참조(조회용, nullable). 관리자가 신고 없이 직접
    // 정지시킨 경우엔 null - "신고 없이 직접 조치됨"이라는 뜻으로 그대로 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private Report report;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @Builder
    private SanctionRecord(User targetUser, LocalDateTime suspendedUntil, String adminNote, Report report) {
        this.targetUser = targetUser;
        this.suspendedUntil = suspendedUntil;
        this.adminNote = adminNote;
        this.report = report;
    }
}