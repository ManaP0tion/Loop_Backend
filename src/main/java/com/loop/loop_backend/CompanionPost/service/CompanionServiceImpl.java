package com.loop.loop_backend.CompanionPost.service;

import com.loop.loop_backend.Block.repository.BlockRepository;
import com.loop.loop_backend.CompanionHeart.repository.CompanionHeartRepository;
import com.loop.loop_backend.CompanionPost.domain.CompanionActivity;
import com.loop.loop_backend.CompanionPost.domain.CompanionPost;
import com.loop.loop_backend.CompanionPost.domain.WatchDay;
import com.loop.loop_backend.CompanionPost.domain.WatchStyle;
import com.loop.loop_backend.CompanionPost.dto.CompanionDetailResponseDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionRequestDto;
import com.loop.loop_backend.CompanionPost.dto.CompanionResponseDto;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostRepository;
import com.loop.loop_backend.CompanionPost.repository.CompanionPostSpecifications;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.repository.ConcertRepository;
import com.loop.loop_backend.FavoriteArtist.repository.FavoriteArtistRepository;
import com.loop.loop_backend.HashTag.repository.UserHashtagRepository;
import com.loop.loop_backend.User.domain.AgeGroup;
import com.loop.loop_backend.User.domain.Gender;
import com.loop.loop_backend.User.domain.Status;
import com.loop.loop_backend.User.domain.User;
import com.loop.loop_backend.User.repository.UserRepository;
import com.loop.loop_backend.common.dto.PageResponseDto;
import com.loop.loop_backend.common.exception.BusinessException;
import com.loop.loop_backend.common.exception.ErrorCode;
import com.loop.loop_backend.common.time.ExpiryCutoff;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanionServiceImpl implements CompanionService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

    private final CompanionPostRepository companionPostRepository;
    private final UserRepository userRepository;
    private final ConcertRepository concertRepository;
    private final UserHashtagRepository userHashtagRepository;
    private final FavoriteArtistRepository favoriteArtistRepository;
    private final BlockRepository blockRepository;
    private final CompanionHeartRepository companionHeartRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void createCompanion(Long userId, CompanionRequestDto requestDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Concert concert = concertRepository.findById(requestDto.getConcertId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONCERT_NOT_FOUND));

        if (companionPostRepository.existsByUserAndConcert_IdAndWatchDay(
                user, requestDto.getConcertId(), requestDto.getWatchDay())) {
            throw new BusinessException(ErrorCode.COMPANION_POST_ALREADY_EXISTS);
        }

        try {
            companionPostRepository.saveAndFlush(CompanionPost.builder()
                    .user(user)
                    .concert(concert)
                    .watchDay(requestDto.getWatchDay())
                    .activities(requestDto.getActivities())
                    .watchStyle(requestDto.getWatchStyle())
                    .messageToCompanion(requestDto.getMessageToCompanion())
                    .sameGenderOnly(requestDto.isSameGenderOnly())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 사전 존재 체크와 저장 사이의 동시 요청 레이스 - DB 유니크 제약이 최종 방어선
            throw new BusinessException(ErrorCode.COMPANION_POST_ALREADY_EXISTS);
        }
    }

    @Override
    public PageResponseDto<CompanionResponseDto> getWatchingCompanions(Long userId, Long concertId, WatchDay watchDay,
                                                                        Gender gender, List<AgeGroup> ageGroups, Pageable pageable) {
        Gender viewerGender = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .getGender();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concertId),
                CompanionPostSpecifications.watchDayEquals(watchDay),
                CompanionPostSpecifications.hasActivity(CompanionActivity.CONCERT),
                visibleToViewer(userId, viewerGender),
                CompanionPostSpecifications.authorGenderEquals(gender),
                CompanionPostSpecifications.ageGroupIn(ageGroups));

        List<CompanionPost> filtered = companionPostRepository.findAll(spec);

        CompanionPost myPost = companionPostRepository
                .findByUser_IdAndConcert_IdAndWatchDay(userId, concertId, watchDay)
                .orElse(null);

        Comparator<CompanionPost> comparator;
        if (myPost != null && myPost.getActivities().contains(CompanionActivity.CONCERT)) {
            WatchStyle myStyle = myPost.getWatchStyle();
            Set<CompanionActivity> myActivities = myPost.getActivities();
            comparator = Comparator
                    .comparing((CompanionPost post) -> post.getWatchStyle() == myStyle ? 0 : 1)
                    .thenComparing((CompanionPost post) -> -commonActivityCount(post.getActivities(), myActivities))
                    .thenComparing(CompanionPost::getCreatedAt, Comparator.reverseOrder());
        } else {
            comparator = defaultComparator(myPost);
        }

        return paginate(userId, filtered, comparator, pageable);
    }

    @Override
    public PageResponseDto<CompanionResponseDto> getNotWatchingCompanions(Long userId, Long concertId, WatchDay watchDay,
                                                                           Gender gender, List<AgeGroup> ageGroups, Pageable pageable) {
        Gender viewerGender = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .getGender();

        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concertId),
                CompanionPostSpecifications.watchDayEquals(watchDay),
                CompanionPostSpecifications.doesNotHaveActivity(CompanionActivity.CONCERT),
                visibleToViewer(userId, viewerGender),
                CompanionPostSpecifications.authorGenderEquals(gender),
                CompanionPostSpecifications.ageGroupIn(ageGroups));

        List<CompanionPost> filtered = companionPostRepository.findAll(spec);

        CompanionPost myPost = companionPostRepository
                .findByUser_IdAndConcert_IdAndWatchDay(userId, concertId, watchDay)
                .orElse(null);

        return paginate(userId, filtered, defaultComparator(myPost), pageable);
    }

    @Override
    public PageResponseDto<CompanionResponseDto> getAllCompanions(Long userId, Long concertId, WatchDay watchDay,
                                                                    Gender gender, List<AgeGroup> ageGroups, Pageable pageable) {
        Gender viewerGender = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .getGender();

        // 공연 관람 여부(hasActivity/doesNotHaveActivity) 구분 없이 전체 조회 - 아직 내 프로필을 등록하기 전에 보여주는 목록이라
        // watching처럼 내 관람스타일 기준으로 정렬할 근거가 없으므로 등록일자 최신순 고정
        Specification<CompanionPost> spec = Specification.allOf(
                CompanionPostSpecifications.concertIdEquals(concertId),
                CompanionPostSpecifications.watchDayEquals(watchDay),
                visibleToViewer(userId, viewerGender),
                CompanionPostSpecifications.authorGenderEquals(gender),
                CompanionPostSpecifications.ageGroupIn(ageGroups));

        List<CompanionPost> filtered = companionPostRepository.findAll(spec);

        return paginate(userId, filtered, Comparator.comparing(CompanionPost::getCreatedAt, Comparator.reverseOrder()), pageable);
    }

    // 조회자가 대상 프로필을 볼 수 있는지를 결정하는 공통 신원 기반 필터 (매칭 목록 전용 - 본인 글은 매칭 상대가 아니므로 제외)
    private Specification<CompanionPost> visibleToViewer(Long userId, Gender viewerGender) {
        return Specification.allOf(
                CompanionPostSpecifications.userIdNotEquals(userId),
                CompanionPostSpecifications.isVisible(),
                CompanionPostSpecifications.authorNotWithdrawn(),
                CompanionPostSpecifications.hasNoBlockRelationWith(userId),
                CompanionPostSpecifications.respectsSameGenderOnly(viewerGender),
                CompanionPostSpecifications.watchDayNotExpired(ExpiryCutoff.cutoffDate()));
    }

    @Override
    public long countVisibleCompanions(Long concertId, Long userId) {
        Gender viewerGender = viewerGenderOf(userId);
        return companionPostRepository.count(Specification
                .allOf(CompanionPostSpecifications.concertIdEquals(concertId))
                .and(countableByViewerSpec(userId, viewerGender)));
    }

    @Override
    public Map<Long, Long> countVisibleCompanionsByConcert(Collection<Long> concertIds, Long userId) {
        // 콘서트 목록이 비면 조회할 것도 없다. userRepository 조회를 건너뛰어 단건 경로와 부수효과를 맞춘다.
        if (concertIds.isEmpty()) {
            return Map.of();
        }
        Gender viewerGender = viewerGenderOf(userId);

        // 단건 countVisibleCompanions 와 같은 Specification 을 그대로 재사용하고, concertId 조건만
        // = 에서 IN 으로 바꿔 GROUP BY 로 한 번에 센다. 필터 판정 로직은 양쪽이 항상 동일하다.
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<CompanionPost> root = query.from(CompanionPost.class);
        Path<Long> concertId = root.get("concert").get("id");

        query.multiselect(concertId, cb.count(root))
                .where(cb.and(
                        concertId.in(concertIds),
                        countableByViewerSpec(userId, viewerGender).toPredicate(root, query, cb)))
                .groupBy(concertId);

        return entityManager.createQuery(query).getResultList().stream()
                .collect(Collectors.toMap(t -> t.get(0, Long.class), t -> t.get(1, Long.class)));
    }

    private Gender viewerGenderOf(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND))
                .getGender();
    }

    // 목록(visibleToViewer)과 달리 본인 글도 포함해서 셈 - 단, isVisible/authorNotWithdrawn/watchDayNotExpired는 그대로 적용되고,
    // hasNoBlockRelationWith·respectsSameGenderOnly는 본인 글에는 항상 자명하게 통과한다(자기 자신과는 차단·이성공개제한이 성립하지 않음)
    private Specification<CompanionPost> countableByViewerSpec(Long userId, Gender viewerGender) {
        return Specification.allOf(
                CompanionPostSpecifications.isVisible(),
                CompanionPostSpecifications.authorNotWithdrawn(),
                CompanionPostSpecifications.hasNoBlockRelationWith(userId),
                CompanionPostSpecifications.respectsSameGenderOnly(viewerGender),
                CompanionPostSpecifications.watchDayNotExpired(ExpiryCutoff.cutoffDate()));
    }

    // 관람 스타일 우선순위가 적용되지 않는 기본 정렬: 내 프로필이 있으면 공통 활동 많은 순, 없으면 등록일자 최신순
    private Comparator<CompanionPost> defaultComparator(CompanionPost myPost) {
        if (myPost != null) {
            Set<CompanionActivity> myActivities = myPost.getActivities();
            return Comparator
                    .<CompanionPost>comparingInt(post -> -commonActivityCount(post.getActivities(), myActivities))
                    .thenComparing(CompanionPost::getCreatedAt, Comparator.reverseOrder());
        }
        return Comparator.comparing(CompanionPost::getCreatedAt, Comparator.reverseOrder());
    }


    private int commonActivityCount(Set<CompanionActivity> a, Set<CompanionActivity> b) {
        return (int) a.stream().filter(b::contains).count();
    }

    private PageResponseDto<CompanionResponseDto> paginate(Long userId, List<CompanionPost> posts,
                                                            Comparator<CompanionPost> comparator, Pageable pageable) {
        List<CompanionPost> sorted = posts.stream().sorted(comparator).toList();

        int start = Math.min((int) pageable.getOffset(), sorted.size());
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        List<CompanionPost> pageItems = sorted.subList(start, end);

        List<Long> pageItemIds = pageItems.stream().map(CompanionPost::getId).toList();
        Set<Long> heartedIds = pageItemIds.isEmpty()
                ? Collections.emptySet()
                : companionHeartRepository.findHeartedCompanionPostIds(userId, pageItemIds);

        List<CompanionResponseDto> content = pageItems.stream()
                .map(post -> new CompanionResponseDto(post, heartedIds.contains(post.getId())))
                .toList();

        return new PageResponseDto<>(content, sorted.size(), end < sorted.size());
    }

    @Override
    public CompanionDetailResponseDto getCompanion(Long userId, Long companionId) {
        CompanionPost post = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        // 관람일이 지난 프로필은 삭제된 것과 동일하게 취급 (채팅방의 프로필 조회 링크 포함)
        if (post.isExpired(LocalDateTime.now(ZONE_KST))) {
            throw new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND);
        }

        Long authorId = post.getUser().getId();

        if (post.getUser().getStatus() == Status.WITHDRAWN) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }

        if (!userId.equals(authorId) && blockRepository.existsBlockBetween(userId, List.of(authorId))) {
            throw new BusinessException(ErrorCode.BLOCKED_USER);
        }

        if (!post.isVisible() && !userId.equals(authorId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<CompanionDetailResponseDto.HashtagSummary> hashtags = userHashtagRepository.findAllByUser(post.getUser())
                .stream()
                .map(tag -> new CompanionDetailResponseDto.HashtagSummary(tag.getId(), tag.getTag()))
                .toList();

        List<String> preferredArtistNames = favoriteArtistRepository.findAllByUser(post.getUser())
                .stream()
                .map(fa -> fa.getArtist().getName())
                .toList();

        boolean isHearted = companionHeartRepository.existsByUser_IdAndCompanionPost_Id(userId, companionId);

        return new CompanionDetailResponseDto(post, hashtags, preferredArtistNames, isHearted);
    }

    @Override
    @Transactional
    public CompanionResponseDto updateCompanion(Long userId, Long companionId, CompanionRequestDto requestDto) {
        CompanionPost post = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        post.update(requestDto.getActivities(), requestDto.getWatchStyle(), requestDto.getMessageToCompanion(),
                requestDto.isSameGenderOnly());

        // 본인 글이라 하트 자체가 불가능하므로 항상 false
        return new CompanionResponseDto(post, false);
    }

    @Override
    @Transactional
    public void updateVisibility(Long userId, Long companionId, boolean visible) {
        CompanionPost post = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        post.toggleVisible(visible);
    }

    @Override
    public List<CompanionResponseDto> getMyCompanions(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(ZONE_KST);
        List<CompanionPost> posts = companionPostRepository.findAllByUser(user).stream()
                .filter(post -> !post.isExpired(now))
                .toList();
        if (posts.isEmpty()) {
            throw new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND);
        }

        // 본인 글이라 하트 자체가 불가능하므로 항상 false
        return posts.stream()
                .map(post -> new CompanionResponseDto(post, false))
                .toList();
    }

    @Override
    public CompanionResponseDto getMyCompanion(Long userId, Long concertId, WatchDay watchDay) {
        CompanionPost post = companionPostRepository.findByUser_IdAndConcert_IdAndWatchDay(userId, concertId, watchDay)
                .orElse(null);

        // 프로필 미등록은 에러가 아니라 정상 상태(목록 조회 화면에서 흔히 조회됨) - null 반환
        if (post == null || post.isExpired(LocalDateTime.now(ZONE_KST))) {
            return null;
        }

        // 본인 글이라 하트 자체가 불가능하므로 항상 false
        return new CompanionResponseDto(post, false);
    }

    @Override
    @Transactional
    public void deleteCompanion(Long userId, Long companionId) {
        CompanionPost post = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        companionPostRepository.delete(post);
    }

    @Override
    public boolean existsMyCompanion(Long userId, Long companionId) {
        CompanionPost target = companionPostRepository.findById(companionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMPANION_POST_NOT_FOUND));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return companionPostRepository.existsByUserAndConcert_IdAndWatchDay(
                user, target.getConcert().getId(), target.getWatchDay());
    }
}