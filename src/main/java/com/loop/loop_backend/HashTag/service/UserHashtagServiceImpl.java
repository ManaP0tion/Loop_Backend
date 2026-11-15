package com.loop.loop_backend.HashTag.service;

import com.loop.loop_backend.HashTag.domain.UserHashtag;
import com.loop.loop_backend.HashTag.dto.HashtagResponseDto;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserHashtagServiceImpl implements UserHashtagService {

    private static final int MAX_HASHTAG_COUNT = 3;

    private final UserHashtagRepository userHashtagRepository;
    private final UserRepository userRepository;

    @Override
    public List<HashtagResponseDto> getHashtags(Long userId) {
        User user = getUser(userId);
        if (user.getStatus() == Status.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }
        return userHashtagRepository.findAllByUser(user).stream()
                .map(HashtagResponseDto::from)
                .toList();
    }

    @Override
    @Transactional
    public HashtagResponseDto addHashtag(Long userId, String rawTag) {
        User user = getUser(userId);
        String tag = rawTag.strip();

        if (userHashtagRepository.countByUser(user) >= MAX_HASHTAG_COUNT) {
            throw new BusinessException(ErrorCode.LIMIT_HASHTAG);
        }
        if (userHashtagRepository.existsByUserAndTag(user, tag)) {
            throw new BusinessException(ErrorCode.DUPLICATE_HASHTAG);
        }

        UserHashtag saved = userHashtagRepository.save(
                UserHashtag.builder()
                        .user(user)
                        .tag(tag)
                        .build()
        );
        return HashtagResponseDto.from(saved);
    }

    @Override
    @Transactional
    public void deleteHashtag(Long userId, Long hashtagId) {
        User user = getUser(userId);
        UserHashtag hashtag = userHashtagRepository.findByIdAndUser(hashtagId, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.HASHTAG_NOT_FOUND));
        userHashtagRepository.delete(hashtag);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}