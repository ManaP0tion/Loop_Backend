package com.loop.loop_backend.Concert.service;

import com.loop.loop_backend.Artist.domain.Artist;
import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertPastDetailDto;
import com.loop.loop_backend.Concert.dto.ConcertPeriod;
import com.loop.loop_backend.Concert.dto.ConcertPeriodDto;
import com.loop.loop_backend.Concert.dto.ConcertRequestDto;
import com.loop.loop_backend.Concert.dto.ConcertResponseDto;
import com.loop.loop_backend.Concert.dto.ConcertSection;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.Concert.dto.ConcertUpcomingDetailDto;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.CompanionPost.service.CompanionService;
import com.loop.loop_backend.ConcertScrap.repository.ConcertScrapRepository;
import com.loop.loop_backend.Storage.service.S3StorageService;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.time.ExpiryCutoff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final ConcertScrapRepository concertScrapRepository;

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
                .price(requestDto.getPrice())
                .ticketUrl(requestDto.getTicketUrl())
                .showtime(requestDto.getShowtime())
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
                requestDto.getCategory(), requestDto.getPrice(), requestDto.getTicketUrl(),
                requestDto.getShowtime());
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

    @Override
    public ConcertUpcomingDetailDto getUpcomingDetail(Long id, Long userId) {
        Concert concert = findConcertOrThrow(id);
        // 이미 지난 공연이면 예정 공연 상세로 보여주지 않는다 - 잘못된 엔드포인트 호출을 404로 처리(getPastDetail과 대칭).
        // 날짜 미정 공연은 isPast가 false라서 예정으로 취급된다.
        if (concert.isPast(ExpiryCutoff.cutoffDate())) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
        }
        // D-day는 노출 만료 기준(오전 10시 리셋)과 별개로 한국 날짜 기준 남은 일수다.
        return ConcertUpcomingDetailDto.from(concert, LocalDate.now(ZoneId.of("Asia/Seoul")), isScrapped(userId, id));
    }

    @Override
    public ConcertPastDetailDto getPastDetail(Long id, Long userId) {
        Concert concert = findConcertOrThrow(id);
        if (!concert.isPast(ExpiryCutoff.cutoffDate())) {
            // id는 존재하지만 지난 공연이 아님(예정 공연) - 잘못된 엔드포인트 호출을 404로 처리
            throw new BusinessException(ErrorCode.CONCERT_NOT_FOUND);
        }
        return ConcertPastDetailDto.from(concert, isScrapped(userId, id));
    }

    // 상세는 비로그인도 조회 가능하다(GET /api/concerts/** permitAll) - 비로그인이면 스크랩 여부는 항상 false.
    private boolean isScrapped(Long userId, Long concertId) {
        return userId != null && concertScrapRepository.existsByUser_IdAndConcert_Id(userId, concertId);
    }

    @Override
    public ConcertPeriodDto getPeriod(Long id) {
        // 딥링크 등 목록을 안 거친 진입 경로용 힌트 조회 - 여기서는 예외 없이 판정 결과만 그대로 돌려준다.
        // period 불일치 검증은 실제 데이터를 주는 getUpcomingDetail/getPastDetail이 각자 책임진다.
        Concert concert = findConcertOrThrow(id);
        ConcertPeriod period = concert.isPast(ExpiryCutoff.cutoffDate()) ? ConcertPeriod.PAST : ConcertPeriod.UPCOMING;
        return new ConcertPeriodDto(period);
    }

    @Override
    public List<ConcertSummaryDto> searchConcertsByTitle(String title, ConcertSection section, ConcertPeriod period, Long userId) {
        List<ConcertCategory> categories = (section != null)
                ? section.getCategories()
                : List.of(ConcertCategory.values());
        LocalDate cutoff = ExpiryCutoff.cutoffDate();

        if (period == ConcertPeriod.UPCOMING) {
            return concertRepository.searchUpcomingOrUndatedByTitleAndCategories(title, categories, cutoff).stream()
                    .map(ConcertSummaryDto::from)
                    .toList();
        }
        if (period == ConcertPeriod.PAST) {
            return concertRepository.searchPastByTitleAndCategories(title, categories, cutoff).stream()
                    .map(ConcertSummaryDto::from)
                    .toList();
        }
        // 전체검색(period 미지정): 예정 목록 뒤에 지난 목록을 그대로 이어붙인다.
        List<ConcertSummaryDto> upcoming = concertRepository
                .searchUpcomingOrUndatedByTitleAndCategories(title, categories, cutoff).stream()
                .map(ConcertSummaryDto::from)
                .toList();
        List<ConcertSummaryDto> past = concertRepository
                .searchPastByTitleAndCategories(title, categories, cutoff).stream()
                .map(ConcertSummaryDto::from)
                .toList();
        return Stream.concat(upcoming.stream(), past.stream()).toList();
    }

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
