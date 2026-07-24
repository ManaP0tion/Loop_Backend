package com.loop.loop_backend.Block.service;

import com.loop.loop_backend.Block.domain.Block;
import com.loop.loop_backend.Block.dto.BlockedUserResponseDto;
import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.Chat.service.ChatService;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockServiceImpl implements BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;

    @Override
    @Transactional
    public void block(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }

        User me = getUser(userId);
        User target = getUser(targetUserId);

        if (blockRepository.existsByBlockerAndBlocked(me, target)) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }

        try {
            blockRepository.saveAndFlush(Block.builder()
                    .blocker(me)
                    .blocked(target)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 사전 존재 체크와 저장 사이의 동시 요청 레이스 - DB 유니크 제약이 최종 방어선
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }

        chatService.hideDirectRoomForUser(userId, targetUserId);
    }

    @Override
    @Transactional
    public void unblock(Long userId, Long targetUserId) {
        User me = getUser(userId);
        User target = getUser(targetUserId);

        Block block = blockRepository.findByBlockerAndBlocked(me, target)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOCK_NOT_FOUND));

        blockRepository.delete(block);
    }

    @Override
    public List<BlockedUserResponseDto> getBlockedUsers(Long userId) {
        User me = getUser(userId);
        return blockRepository.findAllByBlocker(me).stream()
                .filter(block -> block.getBlocked().getStatus() != Status.WITHDRAWN)
                .map(BlockedUserResponseDto::new)
                .toList();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}