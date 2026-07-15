package com.loop.loop_backend.Block.service;

import com.loop.loop_backend.Block.dto.BlockedUserResponseDto;

import java.util.List;

public interface BlockService {

    void block(Long userId, Long targetUserId);
    void unblock(Long userId, Long targetUserId);
    List<BlockedUserResponseDto> getBlockedUsers(Long userId);
}