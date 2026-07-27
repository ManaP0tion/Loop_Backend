package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.CompanionPost.service.CompanionService;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.time.ExpiryCutoff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertServiceImpl implements ConcertService {

    private final ConcertRepository concertRepository;
    private final ArtistRepository artistRepository;
    private final CompanionPostRepository companionPostRepository;
    private final CompanionService companionService;
    private final S3StorageService s3StorageService;

    @Override
    @Transactional
    public ConcertResponseDto createConcert(ConcertRequestDto requestDto, MultipartFile image) {
        Artist artist = resolveArtist(requestDto.getArtistId());
        Concert concert = Concert.builder()
                .artist(artist)
                .title(requestDto.getTitle())
                .venue(requestDto.getVenue())
                .startDate(requestDto.getStartDate())
                .endDate(requestDto.getEndDate())
                .category(requestDto.getCategory())
                .build();
        // IDENTITY 전략이라 save() 시점에 즉시 ID가 확정됨 -> 그 ID를 S3 키로 써서 바로 업로드 가능
        concertRepository.save(concert);

        String posterUrl = resolvePosterUrl(concert.getId(), image, null);
        if (posterUrl != null) {
            concert.updatePosterUrl(posterUrl);
        }
        return ConcertResponseDto.from(concert, companionPostRepository.countByConcert_Id(concert.getId()));
    }

    @Override
    @Transactional
    public ConcertResponseDto updateConcert(Long id, ConcertRequestDto requestDto, MultipartFile image) {
        Concert concert = findConcertOrThrow(id);
        Artist artist = resolveArtist(requestDto.getArtistId());
        // 새 이미지가 없으면 기존 posterUrl을 그대로 유지 (수정 시 이미지 없이 다른 필드만 바꾸는 경우 대비)
        String posterUrl = resolvePosterUrl(id, image, concert.getPosterUrl());
        concert.update(artist, requestDto.getTitle(), posterUrl,
                requestDto.getVenue(), requestDto.getStartDate(), requestDto.getEndDate(),
                requestDto.getCategory());
        return ConcertResponseDto.from(concert, companionPostRepository.countByConcert_Id(concert.getId()));
    }

    // image가 없으면(선택 파라미터) currentPosterUrl을 그대로 반환, 있으면 S3에 업로드하고 새 URL을 반환
    private String resolvePosterUrl(Long concertId, MultipartFile image, String currentPosterUrl) {
        if (image == null || image.isEmpty()) {
            return currentPosterUrl;
        }
        return s3StorageService.uploadPublic("concerts", concertId, image);
    }

    @Override
    @Transactional
    public void deleteConcert(Long id) {
        concertRepository.delete(findConcertOrThrow(id));
    }

    @Override
    public ConcertResponseDto getConcertById(Long id, Long userId) {
        Concert concert = findConcertOrThrow(id);

        return ConcertResponseDto.from(concert, companionService.countVisibleCompanions(concert.getId(), userId));

    }

    // 호출하는 곳이 없어 주석 처리 (필요해지면 userId 파라미터 추가해서 복구)
    // @Override
    // public ConcertResponseDto getConcertByTitle(String title) {
    //     Concert concert = concertRepository.findByTitle(title)
    //             .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    //     return ConcertResponseDto.from(concert, companionPostRepository.countByConcert_Id(concert.getId()));
    // }

    @Override
    public List<ConcertResponseDto> searchConcertsByTitle(String title, Long userId) {
        return concertRepository.searchUpcomingOrUndatedByTitle(title, ExpiryCutoff.cutoffDate()).stream()
                .map(concert -> toResponseDto(concert, userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertResponseDto> getAllConcerts(Long userId) {
        return concertRepository.findUpcomingOrUndated(ExpiryCutoff.cutoffDate()).stream()
                .map(concert -> toResponseDto(concert, userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertResponseDto> getConcertsByCategory(ConcertCategory category, Long userId) {
        return concertRepository.findUpcomingOrUndatedByCategory(category, ExpiryCutoff.cutoffDate()).stream()
                .map(concert -> toResponseDto(concert, userId))
                .collect(Collectors.toList());
    }

    @Override
    public List<ConcertResponseDto> getConcertsByArtist(Long artistId, Long userId) {
        return concertRepository.findUpcomingOrUndatedByArtistId(artistId, ExpiryCutoff.cutoffDate()).stream()
                .map(concert -> toResponseDto(concert, userId))
                .collect(Collectors.toList());
    }

    private ConcertResponseDto toResponseDto(Concert concert, Long userId) {
        return ConcertResponseDto.from(concert, companionService.countVisibleCompanions(concert.getId(), userId));
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
