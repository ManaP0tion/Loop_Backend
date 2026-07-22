package com.loop.loop_backend.Chat.domain;

import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // DIRECT(1:1) 채팅방은 post가 없을 수 있음
    // 동행글(CompanionPost)이 삭제되면 이 FK(post_id)를 DB가 자동으로 null 처리한다 (ON DELETE SET NULL).
    // 채팅방/대화 이력은 지우지 않고 "원본 글 참조"만 끊어내는 설계.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = true)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CompanionPost post;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ChatRoomType type;

    @Column(name = "name", length = 100)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void assignPost(CompanionPost post) {
        this.post = post;
    }
}