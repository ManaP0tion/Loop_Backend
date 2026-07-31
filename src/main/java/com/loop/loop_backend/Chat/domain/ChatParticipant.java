package com.loop.loop_backend.Chat.domain;

import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_participants")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ParticipantStatus status;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    // 회원 탈퇴로 인해 leave() 될 때만 채워짐(일반 나가기는 건드리지 않음).
    // null이면 이 방의 메시지 전체를 볼 수 있고, 값이 있으면 이 시각 이후 메시지만 조회/미리보기/안읽음 집계에 노출된다.
    // 탈퇴 후 재가입해서 rejoin() 되어도 이 값은 그대로 유지되어, 탈퇴 이전 대화 이력이 다시 보이지 않는다.
    @Column(name = "message_visible_from")
    private LocalDateTime messageVisibleFrom;

    public void hideMessagesBefore(LocalDateTime cutoff) {
        this.messageVisibleFrom = cutoff;
    }

    public void leave() {
        this.status = ParticipantStatus.LEFT;
        this.leftAt = LocalDateTime.now();
    }

    public void rejoin() {
        this.status = ParticipantStatus.ACTIVE;
        this.leftAt = null;
    }
}