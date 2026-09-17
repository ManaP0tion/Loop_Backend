package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.dto.ConcertSection;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertServiceImpl implements ConcertService {

    private final ConcertRepository concertRepository;
    private final ArtistRepository artistRepository;
    private final CompanionPostRepository companionPostRepository;
    // 지금 조회 계열은 안 쓰지만, 동행 인원수까지 보여줄 화면(예: 페스티벌 목록)이 생기면
    // toResponseDtos()에서 다시 쓸 예정이라 필드/메서드 그대로 남겨둠.
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
    public ConcertSummaryDto getConcertById(Long id, Long userId) {
        return ConcertSummaryDto.from(findConcertOrThrow(id));
    }

    // 호출하는 곳이 없어 주석 처리 (필요해지면 userId 파라미터 추가해서 복구)
    // @Override
    // public ConcertResponseDto getConcertByTitle(String title) {
    //     Concert concert = concertRepository.findByTitle(title)
    //             .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));
    //     return ConcertResponseDto.from(concert, companionPostRepository.countByConcert_Id(concert.getId()));
    // }

    @Override
    public List<ConcertSummaryDto> searchConcertsByTitle(String title, Long userId) {
        return concertRepository.searchUpcomingOrUndatedByTitle(title, ExpiryCutoff.cutoffDate()).stream()
                .map(ConcertSummaryDto::from)
                .toList();
    }

    // section/period 조회(getConcertsBySection)로 대체돼 호출하는 곳이 없어 주석 처리
    // @Override
    // public List<ConcertResponseDto> getAllConcerts(Long userId, ConcertSort sort) {
    //     return toResponseDtos(concertRepository.findUpcomingOrUndated(ExpiryCutoff.cutoffDate()), userId).stream()
    //             .sorted(sort.comparator())
    //             .collect(Collectors.toList());
    // }
    //
    // @Override
    // public List<ConcertResponseDto> getConcertsByCategory(ConcertCategory category, Long userId, ConcertSort sort) {
    //     return toResponseDtos(
    //             concertRepository.findUpcomingOrUndatedByCategory(category, ExpiryCutoff.cutoffDate()), userId).stream()
    //             .sorted(sort.comparator())
    //             .collect(Collectors.toList());
    // }

    @Override
    public List<ConcertSummaryDto> getConcertsByArtist(Long artistId, Long userId) {
        return concertRepository.findUpcomingOrUndatedByArtistId(artistId, ExpiryCutoff.cutoffDate()).stream()
                .map(ConcertSummaryDto::from)
                .toList();
    }

    @Override
    public List<ConcertSummaryDto> getConcertsBySection(ConcertSection section, ConcertPeriod period, Long userId) {
        List<ConcertCategory> categories = section.getCategories();
        LocalDate cutoff = ExpiryCutoff.cutoffDate();
        List<Concert> concerts = (period == ConcertPeriod.UPCOMING)
                ? concertRepository.findUpcomingOrUndatedByCategories(categories, cutoff)
                : concertRepository.findPastByCategories(categories, cutoff);
        return concerts.stream().map(ConcertSummaryDto::from).toList();
    }

    // 콘서트별 동행 프로필 수를 한 번에 조회한다. 콘서트마다 count 쿼리를 날리면 목록 길이에
    // 비례해 쿼리가 늘어나므로(N+1), GROUP BY 한 번으로 받아온 뒤 매핑만 한다.
    private List<ConcertResponseDto> toResponseDtos(List<Concert> concerts, Long userId) {
        List<Long> concertIds = concerts.stream().map(Concert::getId).toList();
        Map<Long, Long> companionCounts = companionService.countVisibleCompanionsByConcert(concertIds, userId);

        return concerts.stream()
                // 동행 프로필이 0건인 콘서트는 GROUP BY 결과에 없다 - 목록에서 빠지면 안 되므로 0으로 채운다.
                .map(concert -> ConcertResponseDto.from(
                        concert, companionCounts.getOrDefault(concert.getId(), 0L)))
                .collect(Collectors.toList());
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
