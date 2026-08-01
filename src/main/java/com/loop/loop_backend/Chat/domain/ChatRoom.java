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

    // post.concert.id를 그때그때 타고 들어가지 않고 assignPost() 시점에 값만 복제해둔 것.
    // post는 회원 탈퇴 등으로 하드 삭제되면 SET NULL로 끊기지만, 이 채팅이 어떤 공연 얘기였는지는
    // post 삭제 여부와 무관하게 남아있어야 해서 방 자체에 id만 독립적으로 보관한다.
    // Concert 엔티티 연관관계로 걸 필요 없이(응답에서 id만 쓰고 엔티티 탐색은 안 함) 단순 컬럼으로 충분.
    @Column(name = "concert_id")
    private Long concertId;

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
        // post.getConcert()는 LAZY 프록시지만 getId()는 프록시 초기화(추가 쿼리) 없이 바로 읽힘
        this.concertId = post.getConcert() != null ? post.getConcert().getId() : null;
    }
}