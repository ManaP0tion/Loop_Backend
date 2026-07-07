package com.loop.loop_backend.Artist.service;

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
}
