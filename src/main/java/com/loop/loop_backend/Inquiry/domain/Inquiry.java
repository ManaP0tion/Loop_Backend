package com.loop.loop_backend.Inquiry.domain;

import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inquiries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = InquiryTypeConverter.class)
    @Column(name = "type", nullable = false, length = 30)
    private InquiryType type;

    @Convert(converter = InquiryStatusConverter.class)
    @Column(name = "status", length = 20)
    private InquiryStatus status;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // 답장받을 이메일 스냅샷. user.email(계정 이메일)에 기대지 않는 이유: 탈퇴 시 계정 이메일이 null 처리되므로,
    // 탈퇴 전에 남긴 문의도 계속 답장 가능하도록 접수 시점 값을 별도로 들고 있는다.
    @Column(name = "email", length = 255)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Inquiry(User user, InquiryType type, String title, String content, String email) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
        this.email = email;
        this.status = InquiryStatus.PENDING;
    }

    public void updateStatus(InquiryStatus status) {
        this.status = status;
    }

    /** 컨버터 기본값과 별개로, 조회 시 레거시 null 방어 */
    public InquiryStatus getStatus() {
        return status == null ? InquiryStatus.PENDING : status;
    }
}