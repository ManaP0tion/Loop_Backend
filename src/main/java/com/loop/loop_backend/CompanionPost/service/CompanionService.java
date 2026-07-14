package com.loop.loop_backend.CompanionPost.service;

import com.loop.loop_backend.CompanionPost.dto.CompanionDetailResponseDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionRequestDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;

import java.util.List;

public interface CompanionService {

    void createCompanion(Long userId, CompanionRequestDto requestDto);

    CompanionDetailResponseDto getCompanion(Long userId, Long companionId);

    CompanionResponseDto updateCompanion(Long userId, Long companionId, CompanionRequestDto requestDto);

    void updateVisibility(Long userId, Long companionId, boolean visible);

    List<CompanionResponseDto> getMyCompanions(Long userId);

    void deleteCompanion(Long userId, Long companionId);

    boolean existsMyCompanion(Long userId, Long companionId);
}