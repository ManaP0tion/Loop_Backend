package com.loop.loop_backend.Artist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.dto.ArtistRequestDto;
import com.loop.loop_backend.Artist.dto.ArtistResponseDto;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistServiceImpl implements ArtistService {

    private final ArtistRepository artistRepository;

    @Override
    @Transactional
    public ArtistResponseDto createArtist(ArtistRequestDto requestDto) {
        Artist artist = Artist.builder()
                .name(requestDto.getName())
                .baseName(requestDto.getBaseName())
                .nameKo(requestDto.getNameKo())
                .nameAlias(requestDto.getNameAlias())
                .imageUrl(requestDto.getImageUrl())
                .build();
        return ArtistResponseDto.from(artistRepository.save(artist));
    }

    @Override
    @Transactional
    public ArtistResponseDto updateArtist(Long id, ArtistRequestDto requestDto) {
        Artist artist = findArtistOrThrow(id);
        artist.update(requestDto.getName(), requestDto.getBaseName(), requestDto.getNameKo(), requestDto.getNameAlias(), requestDto.getImageUrl());
        return ArtistResponseDto.from(artist);
    }

    @Override
    @Transactional
    public void deleteArtist(Long id) {
        artistRepository.delete(findArtistOrThrow(id));
    }

    @Override
    public ArtistResponseDto getArtistById(Long id) {
        return ArtistResponseDto.from(findArtistOrThrow(id));
    }

    @Override
    public ArtistResponseDto getArtistByName(String name) {
        Artist artist = artistRepository.findByName(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
        return ArtistResponseDto.from(artist);
    }

    @Override
    public List<ArtistResponseDto> getAllArtists() {
        return artistRepository.findAll().stream()
                .map(ArtistResponseDto::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArtistResponseDto> searchArtists(String query) {
        return artistRepository.searchByAllNames(query).stream()
                .map(ArtistResponseDto::from)
                .collect(Collectors.toList());
    }

    private Artist findArtistOrThrow(Long id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
    }
}
