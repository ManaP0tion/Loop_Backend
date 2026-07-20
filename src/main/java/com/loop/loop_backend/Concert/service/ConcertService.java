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
    ConcertResponseDto getConcertById(Long id);
    ConcertResponseDto getConcertByTitle(String title);
    List<ConcertResponseDto> searchConcertsByTitle(String title);
    List<ConcertResponseDto> getAllConcerts();
    List<ConcertResponseDto> getConcertsByCategory(ConcertCategory category);
    List<ConcertResponseDto> getConcertsByArtist(Long artistId);
}
