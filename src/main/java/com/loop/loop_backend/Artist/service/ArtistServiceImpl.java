package com.loop.loop_backend.Artist.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.dto.ArtistRequestDto;
import com.loop.loop_backend.Artist.dto.ArtistResponseDto;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.infra.itunes.ItunesArtist;
import com.loop.loop_backend.infra.itunes.ItunesClient;
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
    private final ItunesClient itunesClient;

    @Override
    @Transactional
    public ArtistResponseDto createArtist(ArtistRequestDto requestDto) {
        Artist artist = Artist.builder()
                .name(requestDto.getName())
                .baseName(requestDto.getBaseName())
                .nameKo(requestDto.getNameKo())
                .nameAlias(requestDto.getNameAlias())
                .imageUrl(requestDto.getImageUrl())
                .category(requestDto.getCategory() != null ? requestDto.getCategory() : ConcertCategory.J_POP_ARTIST)
                .build();
        return ArtistResponseDto.from(artistRepository.save(artist));
    }

    @Override
    @Transactional
    public ArtistResponseDto updateArtist(Long id, ArtistRequestDto requestDto) {
        Artist artist = findArtistOrThrow(id);
        artist.update(requestDto.getName(), requestDto.getBaseName(), requestDto.getNameKo(),
                requestDto.getNameAlias(), requestDto.getImageUrl(), requestDto.getCategory());
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

    @Override
    @Transactional
    public ArtistResponseDto linkItunes(Long id, Long itunesArtistId) {
        Artist artist = findArtistOrThrow(id);
        ItunesArtist itunesArtist = itunesClient.lookupArtist(itunesArtistId);
        artist.linkItunes(itunesArtist.artistId(), itunesArtist.artistLinkUrl());
        return ArtistResponseDto.from(artist);
    }

    private Artist findArtistOrThrow(Long id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
    }
}
