package com.loop.loop_backend.Chat.scheduler;

import com.loop.loop_backend.Chat.domain.ChatRoom;
import com.loop.loop_backend.Chat.domain.Message;
import com.loop.loop_backend.Chat.dto.ChatMessageDto;
import com.loop.loop_backend.Chat.repository.ChatRoomRepository;
import com.loop.loop_backend.Chat.repository.MessageRepository;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class ChatSaveScheduler {
    private final RedisTemplate<String, Object> redisTemplate;
    private final MessageRepository messageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void saveMessagesToDb() {
        List<Message> messagesToSave = new ArrayList<>();

        while (true) {
            // 큐에서 메시지를 하나씩 꺼냅니다. (꺼낸 메시지는 Redis에서 삭제됨)
            Object msgObj = redisTemplate.opsForList().leftPop("chat:save_queue");
            if (msgObj == null) {
                break; // 더 이상 대기열에 메시지가 없으면 반복 종료
            }

            ChatMessageDto dto = (ChatMessageDto) msgObj;

            // 엔티티로 변환 (getReferenceById를 사용하면 DB 조회 없이 프록시 객체만 가져와서 매핑 가능)
            ChatRoom chatRoom = chatRoomRepository.getReferenceById(dto.getRoomId());
            User sender = userRepository.getReferenceById(dto.getSenderId());

            Message messageEntity = Message.builder()
                    .chatRoom(chatRoom)
                    .sender(sender)
                    .content(dto.getContent())
                    .isRead(false) // 기본값 설정
                    // created_at은 엔티티의 @CreationTimestamp가 처리하거나 dto.getCreatedAt()을 사용
                    .build();

            messagesToSave.add(messageEntity);
        }

        // 모아둔 메시지가 있으면 JPA saveAll()을 통해 벌크 인서트 처리
        if (!messagesToSave.isEmpty()) {
            messageRepository.saveAll(messagesToSave);
        }
    }
}
