package com.loop.loop_backend.CompanionHeart.service;

import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.CompanionHeart.domain.CompanionHeart;
import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertDetailDto;
import com.loop.loop_backend.CompanionHeart.dto.HeartedConcertSummaryDto;
import com.loop.loop_backend.CompanionHeart.repository.CompanionHeartRepository;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionHeartServiceImpl implements CompanionHeartService {

    private final CompanionHeartRepository companionHeartRepository;
    private final CompanionPostRepository companionPostRepository;
    private final ConcertRepository concertRepository;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;

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

        return byConcert.values().stream()
                .map(this::toSummaryDto)
                .toList();
    }

    private HeartedConcertSummaryDto toSummaryDto(List<CompanionHeart> group) {
        Concert concert = group.get(0).getCompanionPost().getConcert();
        List<CompanionResponseDto> preview = group.stream()
                .limit(2)
                .map(heart -> new CompanionResponseDto(heart.getCompanionPost(), true))
                .toList();

        return new HeartedConcertSummaryDto(
                concert.getId(),
                concert.getTitle(),
                concert.getArtist() != null ? concert.getArtist().getId() : null,
                concert.getArtist() != null ? concert.getArtist().getName() : null,
                concert.getStartDate(),
                concert.getEndDate(),
                group.size(),
                preview);
    }

    @Override
    public HeartedConcertDetailDto getMyHeartedCompanionsByConcert(Long userId, Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        List<CompanionHeart> hearts = filterHiddenOrBlocked(userId,
                companionHeartRepository.findAllByUser_IdAndCompanionPost_Concert_Id(userId, concertId));

        Map<WatchDay, List<CompanionResponseDto>> days = new EnumMap<>(WatchDay.class);
        for (CompanionHeart heart : hearts) {
            days.computeIfAbsent(heart.getCompanionPost().getWatchDay(), k -> new ArrayList<>())
                    .add(new CompanionResponseDto(heart.getCompanionPost(), true));
        }

        return new HeartedConcertDetailDto(
                concert.getId(),
                concert.getTitle(),
                concert.getArtist() != null ? concert.getArtist().getId() : null,
                concert.getArtist() != null ? concert.getArtist().getName() : null,
                concert.getStartDate(),
                concert.getEndDate(),
                days);
    }

    // 하트한 뒤에 작성자가 비공개로 돌렸거나, 나와 작성자 사이에 차단 관계가 생긴 경우 하트탭에서 조용히 제외
    // (개별 조회처럼 에러를 던지면 목록 전체가 깨지므로, 목록에서는 필터링 방식으로 처리)
    private List<CompanionHeart> filterHiddenOrBlocked(Long userId, List<CompanionHeart> hearts) {
        if (hearts.isEmpty()) {
            return hearts;
        }

        List<Long> authorIds = hearts.stream()
                .map(heart -> heart.getCompanionPost().getUser().getId())
                .distinct()
                .toList();
        Set<Long> blockedAuthorIds = new HashSet<>(blockRepository.findBlockedRelatedUserIds(userId, authorIds));

        return hearts.stream()
                .filter(heart -> heart.getCompanionPost().isVisible())
                .filter(heart -> !blockedAuthorIds.contains(heart.getCompanionPost().getUser().getId()))
                .toList();
    }
}