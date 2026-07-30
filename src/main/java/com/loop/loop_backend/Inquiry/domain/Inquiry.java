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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Inquiry(User user, InquiryType type, String title, String content) {
        this.user = user;
        this.type = type;
        this.title = title;
        this.content = content;
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