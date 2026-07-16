package com.loop.loop_backend.CompanionPost.service;

import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.CompanionDetailResponseDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionRequestDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.Gender;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CompanionService {

    void createCompanion(Long userId, CompanionRequestDto requestDto);

    List<CompanionResponseDto> getWatchingCompanions(Long userId, Long concertId, WatchDay watchDay,
                                                      Gender gender, List<AgeGroup> ageGroups, Pageable pageable);

    List<CompanionResponseDto> getNotWatchingCompanions(Long userId, Long concertId, WatchDay watchDay,
                                                         Gender gender, List<AgeGroup> ageGroups, Pageable pageable);

    CompanionDetailResponseDto getCompanion(Long userId, Long companionId);

    CompanionResponseDto updateCompanion(Long userId, Long companionId, CompanionRequestDto requestDto);

    void updateVisibility(Long userId, Long companionId, boolean visible);

    List<CompanionResponseDto> getMyCompanions(Long userId);

    void deleteCompanion(Long userId, Long companionId);

    boolean existsMyCompanion(Long userId, Long companionId);
}