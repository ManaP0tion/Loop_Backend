package com.loop.loop_backend.CompanionPost.service;

import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.dto.CompanionDetailResponseDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionRequestDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
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
public class CompanionServiceImpl implements CompanionService {

    private final CompanionPostRepository companionPostRepository;
    private final UserRepository userRepository;
    private final ConcertRepository concertRepository;
    private final UserHashtagRepository userHashtagRepository;

    @Override
    @Transactional
    public void createCompanion(Long userId, CompanionRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!concertRepository.existsById(requestDto.getConcertId())) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
        }

        if (companionPostRepository.existsByUserAndConcertIdAndWatchDay(
                user, requestDto.getConcertId(), requestDto.getWatchDay())) {
            throw new BusinessException(ErrorCode.COMPANION_POST_ALREADY_EXISTS);
        }

        try {
            companionPostRepository.saveAndFlush(CompanionPost.builder()
                    .user(user)
                    .concertId(requestDto.getConcertId())
                    .watchDay(requestDto.getWatchDay())
                    .preferredGender(requestDto.getPreferredGender())
                    .preferredAgeGroups(requestDto.getPreferredAgeGroups())
                    .activities(requestDto.getActivities())
                    .watchStyle(requestDto.getWatchStyle())
                    .messageToCompanion(requestDto.getMessageToCompanion())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 사전 존재 체크와 저장 사이의 동시 요청 레이스 - DB 유니크 제약이 최종 방어선
            throw new BusinessException(ErrorCode.COMPANION_POST_ALREADY_EXISTS);
        }
    }

    @Override
    public CompanionDetailResponseDto getCompanion(Long userId, Long companionId) {
        CompanionPost post = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        if (!post.isVisible() && !post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<CompanionDetailResponseDto.HashtagSummary> hashtags = userHashtagRepository.findAllByUser(post.getUser())
                .stream()
                .map(tag -> new CompanionDetailResponseDto.HashtagSummary(tag.getId(), tag.getTag()))
                .toList();

        return new CompanionDetailResponseDto(post, hashtags);
    }

    @Override
    @Transactional
    public CompanionResponseDto updateCompanion(Long userId, Long companionId, CompanionRequestDto requestDto) {
        CompanionPost post = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        post.update(requestDto.getPreferredGender(), requestDto.getPreferredAgeGroups(),
                requestDto.getActivities(), requestDto.getWatchStyle(), requestDto.getMessageToCompanion());

        return new CompanionResponseDto(post);
    }

    @Override
    @Transactional
    public void updateVisibility(Long userId, Long companionId, boolean visible) {
        CompanionPost post = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        post.toggleVisible(visible);
    }

    @Override
    public List<CompanionResponseDto> getMyCompanions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<CompanionPost> posts = companionPostRepository.findAllByUser(user);
        if (posts.isEmpty()) {
            throw new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND);
        }

        return posts.stream()
                .map(CompanionResponseDto::new)
                .toList();
    }

    @Override
    @Transactional
    public void deleteCompanion(Long userId, Long companionId) {
        CompanionPost post = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        companionPostRepository.delete(post);
    }

    @Override
    public boolean existsMyCompanion(Long userId, Long companionId) {
        CompanionPost target = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return companionPostRepository.existsByUserAndConcertIdAndWatchDay(
                user, target.getConcertId(), target.getWatchDay());
    }
}