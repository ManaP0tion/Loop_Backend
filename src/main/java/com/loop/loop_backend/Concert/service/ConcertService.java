package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ConcertService {

    ConcertResponseDto createConcert(ConcertRequestDto requestDto, MultipartFile image);
    ConcertResponseDto updateConcert(Long id, ConcertRequestDto requestDto, MultipartFile image);
    void deleteConcert(Long id);
    ConcertResponseDto getConcertById(Long id, Long userId);
    // 호출하는 곳이 없어 주석 처리 (필요해지면 userId 파라미터 추가해서 복구)
    // ConcertResponseDto getConcertByTitle(String title);
    List<ConcertResponseDto> searchConcertsByTitle(String title, Long userId);
    List<ConcertResponseDto> getAllConcerts(Long userId);
    List<ConcertResponseDto> getConcertsByCategory(ConcertCategory category, Long userId);
    List<ConcertResponseDto> getConcertsByArtist(Long artistId, Long userId);
}
