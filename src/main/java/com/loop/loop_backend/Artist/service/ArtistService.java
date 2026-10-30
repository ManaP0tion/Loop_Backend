package com.loop.loop_backend.Artist.service;

import com.loop.loop_backend.Artist.domain.Nationality;
import com.loop.loop_backend.Artist.dto.ArtistRequestDto;
import com.loop.loop_backend.Artist.dto.ArtistResponseDto;

import java.util.List;

public interface ArtistService {

    ArtistResponseDto createArtist(ArtistRequestDto requestDto);
    ArtistResponseDto updateArtist(Long id, ArtistRequestDto requestDto);
    void deleteArtist(Long id);
    ArtistResponseDto getArtistById(Long id);
    ArtistResponseDto getArtistByName(String name);
    List<ArtistResponseDto> getAllArtists();
    /** query·nationality 모두 선택. 둘 다 없으면 전체. */
    List<ArtistResponseDto> searchArtists(String query, Nationality nationality);
    ArtistResponseDto linkItunes(Long id, Long itunesArtistId);
}
