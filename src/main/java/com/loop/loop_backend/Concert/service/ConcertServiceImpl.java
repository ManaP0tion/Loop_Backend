package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.Storage.dto.ImageUploadResponseDto;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertServiceImpl implements ConcertService {

    private final ConcertRepository concertRepository;
    private final ArtistRepository artistRepository;
    private final S3StorageService s3StorageService;

    @Override
    @Transactional
    public ConcertResponseDto createConcert(ConcertRequestDto requestDto) {
        Artist artist = resolveArtist(requestDto.getArtistId());
        Concert concert = Concert.builder()
                .artist(artist)
                .title(requestDto.getTitle())
                .posterUrl(requestDto.getPosterUrl())
                .venue(requestDto.getVenue())
                .startDate(requestDto.getStartDate())
                .endDate(requestDto.getEndDate())
                .category(requestDto.getCategory())
                .build();
        return ConcertResponseDto.from(concertRepository.save(concert));
    }

    @Override
    @Transactional
    public ConcertResponseDto updateConcert(Long id, ConcertRequestDto requestDto) {
        Concert concert = findConcertOrThrow(id);
        Artist artist = resolveArtist(requestDto.getArtistId());
        concert.update(artist, requestDto.getTitle(), requestDto.getPosterUrl(),
                requestDto.getVenue(), requestDto.getStartDate(), requestDto.getEndDate(),
                requestDto.getCategory());
        return ConcertResponseDto.from(concert);
    }

    @Override
    @Transactional
    public void deleteConcert(Long id) {
        concertRepository.delete(findConcertOrThrow(id));
    }

    @Override
    public ConcertResponseDto getConcertById(Long id) {
        return ConcertResponseDto.from(findConcertOrThrow(id));
    }

    @Override
    public ConcertResponseDto getConcertByTitle(String title) {
        Concert concert = concertRepository.findByTitle(title)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
        return ConcertResponseDto.from(concert);
    }

    @Override
    public List<ConcertResponseDto> searchConcertsByTitle(String title) {
        return concertRepository.searchUpcomingOrUndatedByTitle(title, LocalDate.now()).stream()
                .map(ConcertResponseDto::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertResponseDto> getAllConcerts() {
        return concertRepository.findUpcomingOrUndated(LocalDate.now()).stream()
                .map(ConcertResponseDto::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertResponseDto> getConcertsByCategory(ConcertCategory category) {
        return concertRepository.findUpcomingOrUndatedByCategory(category, LocalDate.now()).stream()
                .map(ConcertResponseDto::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertResponseDto> getConcertsByArtist(Long artistId) {
        return concertRepository.findUpcomingOrUndatedByArtistId(artistId, LocalDate.now()).stream()
                .map(ConcertResponseDto::from)
                .collect(Collectors.toList());
    }

    @Override
    public ImageUploadResponseDto uploadConcertImage(Long id, MultipartFile file) {
        // TODO: 운영자 권한 도입 후 인가 체크 추가 필요
        if (!concertRepository.existsById(id)) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
        }
        String url = s3StorageService.uploadPublic("concerts", id, file);
        return new ImageUploadResponseDto(url);
    }

    private Concert findConcertOrThrow(Long id) {
        return concertRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    }

    private Artist resolveArtist(Long artistId) {
        if (artistId == null) return null;
        return artistRepository.findById(artistId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTIST_NOT_FOUND));
    }
}
