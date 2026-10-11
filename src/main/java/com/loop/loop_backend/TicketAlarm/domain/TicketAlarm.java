package com.loop.loop_backend.TicketAlarm.domain;

import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.User.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 공연별 예매 알림 설정. 행이 있으면 켜짐, 없으면 꺼짐(기본 꺼짐).
 * 스크랩과는 별개다 - 스크랩 없이 켤 수 있고, 스크랩을 해제해도 그대로 남는다.
 * 공연·유저가 DB에서 지워지면 함께 지워진다. 회원 탈퇴 시에는 UserServiceImpl이 직접 지운다.
 */
@Entity
@Table(name = "ticket_alarms",
        uniqueConstraints = @UniqueConstraint(name = "uq_ticket_alarm_user_concert_type",
                columnNames = {"user_id", "concert_id", "type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketAlarm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concert_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Concert concert;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private TicketAlarmType type;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private TicketAlarm(User user, Concert concert, TicketAlarmType type) {
        this.user = user;
        this.concert = concert;
        this.type = type;
    }
}