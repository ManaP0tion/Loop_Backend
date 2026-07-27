package com.loop.loop_backend.FavoriteArtist.service;

import com.loop.loop_backend.FavoriteArtist.dto.FavoriteArtistResponseDto;

import java.util.List;

public interface FavoriteArtistService {

    List<FavoriteArtistResponseDto> getFavoriteArtists(Long userId);
    FavoriteArtistResponseDto addFavoriteArtist(Long userId, Long artistId);
    void deleteFavoriteArtist(Long userId, Long favoriteArtistId);
}