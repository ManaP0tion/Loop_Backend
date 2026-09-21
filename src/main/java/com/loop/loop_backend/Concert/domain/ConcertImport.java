package com.loop.loop_backend.Concert.domain;

import com.loop.loop_backend.Artist.domain.Artist;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * KOPIS에서 수집한 공연의 검토 대기 데이터. 관리자가 승인하면 {@link Concert}(운영 데이터)로 넘어간다.
 * 수집 원본 + 1차 필터링 매칭 근거(matchedArtist/matchReason/suggestedCategory)를 담아
 * 관리자가 "왜 이 공연이 이 아티스트로 잡혔는지" 보고 판단할 수 있게 한다.
 *
 * Concert와 동일하게 (kopisId, matchedArtist) 조합이 유니크 — 한 공연이 여러 아티스트에 매칭되면
 * 아티스트마다 import 한 건, 페스티벌은 matchedArtist=null 단일 건.
 */
@Entity
@Table(
    name = "concert_imports",
    uniqueConstraints = @UniqueConstraint(columnNames = {"kopis_id", "matched_artist_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ConcertImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "kopis_id", length = 20, nullable = false)
    private String kopisId;

    // --- KOPIS 원본 값 ---
    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "poster_url", length = 500)
    private String posterUrl;

    @Column(name = "venue", length = 255)
    private String venue;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // --- 1차 필터링 매칭 근거 (관리자 판단 보조) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_artist_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Artist matchedArtist;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_category", length = 30, nullable = false)
    private ConcertCategory suggestedCategory;

    /** 매칭 근거: TITLE_MATCH / CAST_MATCH / JAPAN_FESTIVAL / DOMESTIC_FESTIVAL */
    @Column(name = "match_reason", length = 30)
    private String matchReason;

    // --- 검토 상태 ---
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private ImportStatus status = ImportStatus.PENDING;

    /** 승인 시 생성된 Concert의 id (역추적·재승인 방지) */
    @Column(name = "published_concert_id")
    private Long publishedConcertId;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "collected_at", nullable = false)
    @Builder.Default
    private LocalDateTime collectedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 재수집 시 PENDING인 건만 원본을 최신값으로 갱신한다. 승인/거절된 건은 호출부에서 skip. */
    public void updateFromKopis(String title, String posterUrl, String venue,
                                LocalDate startDate, LocalDate endDate,
                                ConcertCategory suggestedCategory, String matchReason) {
        this.title = title;
        this.posterUrl = posterUrl;
        this.venue = venue;
        this.startDate = startDate;
        this.endDate = endDate;
        this.suggestedCategory = suggestedCategory;
        this.matchReason = matchReason;
    }

    public void markApproved(Long concertId) {
        this.status = ImportStatus.APPROVED;
        this.publishedConcertId = concertId;
        this.reviewedAt = LocalDateTime.now();
    }

    public void markRejected(String reason) {
        this.status = ImportStatus.REJECTED;
        this.rejectReason = reason;
        this.reviewedAt = LocalDateTime.now();
    }
}
