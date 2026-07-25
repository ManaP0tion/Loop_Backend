package com.loop.loop_backend.CompanionPost.service;

import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.CompanionDetailResponseDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionRequestDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.common.dto.PageResponseDto;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CompanionService {

    void createCompanion(Long userId, CompanionRequestDto requestDto);

    PageResponseDto<CompanionResponseDto> getWatchingCompanions(Long userId, Long concertId, WatchDay watchDay,
                                                                 Gender gender, List<AgeGroup> ageGroups, Pageable pageable);

    PageResponseDto<CompanionResponseDto> getNotWatchingCompanions(Long userId, Long concertId, WatchDay watchDay,
                                                                    Gender gender, List<AgeGroup> ageGroups, Pageable pageable);

    PageResponseDto<CompanionResponseDto> getAllCompanions(Long userId, Long concertId, WatchDay watchDay,
                                                            Gender gender, List<AgeGroup> ageGroups, Pageable pageable);

    CompanionDetailResponseDto getCompanion(Long userId, Long companionId);

    CompanionResponseDto updateCompanion(Long userId, Long companionId, CompanionRequestDto requestDto);

    void updateVisibility(Long userId, Long companionId, boolean visible);

    List<CompanionResponseDto> getMyCompanions(Long userId);

    CompanionResponseDto getMyCompanion(Long userId, Long concertId, WatchDay watchDay);

    void deleteCompanion(Long userId, Long companionId);

    boolean existsMyCompanion(Long userId, Long companionId);

    // 조회자가 실제로 볼 수 있는(비공개/차단/탈퇴/동성공개 필터링) 동행 프로필 수
    long countVisibleCompanions(Long concertId, Long userId);
}