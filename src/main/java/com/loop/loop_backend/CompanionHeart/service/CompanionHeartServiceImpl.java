package com.loop.loop_backend.CompanionHeart.service;

import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.CompanionHeart.domain.CompanionHeart;
import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertDetailDto;
import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertSummaryDto;
import com.loop.loop_backend.CompanionHeart.repository.CompanionHeartRepository;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto.HashtagSummary;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionHeartServiceImpl implements CompanionHeartService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
    private static final int PREVIEW_SIZE = 2;

    private final CompanionHeartRepository companionHeartRepository;
    private final CompanionPostRepository companionPostRepository;
    private final ConcertRepository concertRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final UserHashtagRepository userHashtagRepository;

    @Override
    @Transactional
    public void heartCompanion(Long userId, Long companionId) {
        CompanionPost post = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        if (post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SELF_HEART_NOT_ALLOWED);
        }

        if (companionHeartRepository.existsByUser_IdAndCompanionPost_Id(userId, companionId)) {
            return; // 이미 하트한 상태 - 토글 UX라 에러 없이 조용히 성공 처리
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        companionHeartRepository.save(CompanionHeart.builder().user(user).companionPost(post).build());
    }

    @Override
    @Transactional
    public void unheartCompanion(Long userId, Long companionId) {
        // 하트 안 한 상태에서 취소 요청이 와도 토글 UX라 에러 없이 조용히 성공 처리
        companionHeartRepository.findByUser_IdAndCompanionPost_Id(userId, companionId)
                .ifPresent(companionHeartRepository::delete);
    }

    @Override
    public List<HeartedConcertSummaryDto> getMyHeartedConcerts(Long userId) {
        List<CompanionHeart> hearts = filterHiddenOrBlocked(userId,
                companionHeartRepository.findAllByUser_IdOrderByCreatedAtDesc(userId));

        Map<Long, List<CompanionHeart>> byConcert = new LinkedHashMap<>();
        for (CompanionHeart heart : hearts) {
            Long concertId = heart.getCompanionPost().getConcert().getId();
            byConcert.computeIfAbsent(concertId, k -> new ArrayList<>()).add(heart);
        }

        // 성격은 화면에 나가는 미리보기(공연마다 최대 2개)의 작성자 것만 한 번에 읽는다
        Map<Long, List<HashtagSummary>> hashtags = hashtagsByAuthor(byConcert.values().stream()
                .flatMap(group -> group.stream().limit(PREVIEW_SIZE))
                .toList());

        return byConcert.values().stream()
                .map(group -> toSummaryDto(group, hashtags))
                .toList();
    }

    private HeartedConcertSummaryDto toSummaryDto(List<CompanionHeart> group, Map<Long, List<HashtagSummary>> hashtags) {
        Concert concert = group.get(0).getCompanionPost().getConcert();
        List<CompanionResponseDto> preview = group.stream()
                .limit(PREVIEW_SIZE)
                .map(heart -> toResponse(heart, hashtags))
                .toList();

        return new HeartedConcertSummaryDto(
                concert.getId(),
                concert.getTitle(),
                concert.getArtist() != null ? concert.getArtist().getId() : null,
                concert.getArtist() != null ? concert.getArtist().getName() : null,
                concert.getStartDate(),
                concert.getEndDate(),
                concert.getVenue(),
                group.size(),
                preview,
                concert.companionStatusForUsers());
    }

    @Override
    public HeartedConcertDetailDto getMyHeartedCompanionsByConcert(Long userId, Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        List<CompanionHeart> hearts = filterHiddenOrBlocked(userId,
                companionHeartRepository.findAllByUser_IdAndCompanionPost_Concert_IdOrderByCreatedAtDesc(userId, concertId));

        Map<Long, List<HashtagSummary>> hashtags = hashtagsByAuthor(hearts);
        Map<WatchDay, List<CompanionResponseDto>> days = new EnumMap<>(WatchDay.class);
        for (CompanionHeart heart : hearts) {
            days.computeIfAbsent(heart.getCompanionPost().getWatchDay(), k -> new ArrayList<>())
                    .add(toResponse(heart, hashtags));
        }

        return new HeartedConcertDetailDto(
                concert.getId(),
                concert.getTitle(),
                concert.getArtist() != null ? concert.getArtist().getId() : null,
                concert.getArtist() != null ? concert.getArtist().getName() : null,
                concert.getStartDate(),
                concert.getEndDate(),
                days,
                concert.companionStatusForUsers());
    }

    // 하트 탭은 내가 하트한 프로필만 보여주므로 hearted는 항상 true
    private CompanionResponseDto toResponse(CompanionHeart heart, Map<Long, List<HashtagSummary>> hashtags) {
        CompanionPost post = heart.getCompanionPost();
        return new CompanionResponseDto(post, true, hashtags.getOrDefault(post.getUser().getId(), List.of()));
    }

    // 작성자들의 성격을 쿼리 한 번으로 가져와 작성자별로 묶는다 - 프로필마다 조회하면 N+1
    private Map<Long, List<HashtagSummary>> hashtagsByAuthor(List<CompanionHeart> hearts) {
        if (hearts.isEmpty()) {
            return Map.of();
        }
        Set<Long> authorIds = hearts.stream()
                .map(heart -> heart.getCompanionPost().getUser().getId())
                .collect(Collectors.toSet());
        return HashtagSummary.groupByUser(userHashtagRepository.findAllByUser_IdInOrderByIdAsc(authorIds));
    }

    // 하트한 뒤에 작성자가 비공개로 돌렸거나, 나와 작성자 사이에 차단 관계가 생겼거나, 작성자가 탈퇴했거나,
    // 관람일이 지난 경우 하트탭에서 조용히 제외 (개별 조회처럼 에러를 던지면 목록 전체가 깨지므로, 목록에서는 필터링 방식으로 처리)
    private List<CompanionHeart> filterHiddenOrBlocked(Long userId, List<CompanionHeart> hearts) {
        if (hearts.isEmpty()) {
            return hearts;
        }

        List<Long> authorIds = hearts.stream()
                .map(heart -> heart.getCompanionPost().getUser().getId())
                .distinct()
                .toList();
        Set<Long> blockedAuthorIds = new HashSet<>(blockRepository.findBlockedRelatedUserIds(userId, authorIds));
        LocalDateTime now = LocalDateTime.now(ZONE_KST);

        return hearts.stream()
                .filter(heart -> heart.getCompanionPost().isVisible())
                .filter(heart -> heart.getCompanionPost().getUser().getStatus() != Status.WITHDRAWN)
                .filter(heart -> !blockedAuthorIds.contains(heart.getCompanionPost().getUser().getId()))
                .filter(heart -> !heart.getCompanionPost().isExpired(now))
                .toList();
    }
}