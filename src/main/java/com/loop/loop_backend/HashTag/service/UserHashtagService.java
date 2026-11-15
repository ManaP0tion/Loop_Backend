package com.loop.loop_backend.HashTag.service;

import com.loop.loop_backend.HashTag.dto.HashtagResponseDto;

import java.util.List;

public interface UserHashtagService {

    List<HashtagResponseDto> getHashtags(Long userId);
    HashtagResponseDto addHashtag(Long userId, String rawTag);
    void deleteHashtag(Long userId, Long hashtagId);
}