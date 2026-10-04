package com.loop.loop_backend.Concert.dto.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.loop.loop_backend.Concert.domain.Concert;
import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.domain.ConcertShowtime;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 관리자 공연 상세(수정 화면용). 관리자 입력 항목을 모두 담는다. */
@Schema(description = "관리자 공연 상세")
public record AdminConcertDetailResponse(

        @Schema(description = "공연 id", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(description = "KOPIS 공연 id. 직접 등록한 공연이면 null", types = {"string", "null"})
        String kopisId,

        @Schema(description = "공연 유형", requiredMode = Schema.RequiredMode.REQUIRED)
        ConcertCategory category,

        @Schema(description = "공연명", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "공연명 별칭", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> titleAliases,

        @Schema(description = "포스터 URL", types = {"string", "null"})
        String posterUrl,

        @Schema(description = "공연 시작일", types = {"string", "null"})
        LocalDate startDate,

        @Schema(description = "공연 종료일", types = {"string", "null"})
        LocalDate endDate,

        @Schema(description = "DAY별 공연 시각. 기간의 모든 DAY가 순서대로 나오고, 미정인 DAY는 startTime이 null. 기간이 없으면 빈 목록",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<Showtime> showtimes,

        @Schema(description = "연결된 공연장. 없으면 null", types = {"object", "null"})
        VenueSummary venue,

        @Schema(description = "아티스트 목록 (지금은 최대 1명, 페스티벌은 빈 목록)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ArtistSummary> artists,

        @Schema(description = "공개 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean published,

        @Schema(description = "마지막으로 공개로 바뀐 시각", types = {"string", "null"})
        LocalDateTime publishedAt,

        @Schema(description = "예상 곡 수", types = {"integer", "null"})
        Integer expectedSongCount,

        @Schema(description = "숙소 섹션 노출 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean lodgingVisible,

        @Schema(description = "숙소 딥링크", types = {"string", "null"})
        String lodgingUrl,

        @Schema(description = "관련 상품 코드", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> productCodes
) {

    @Schema(description = "DAY별 공연 시각")
    public record Showtime(
            @Schema(description = "DAY 번호(1부터)") int day,
            @Schema(description = "날짜") LocalDate date,
            @Schema(description = "시작 시각(HH:mm). 미정이면 null", types = {"string", "null"})
            @JsonFormat(pattern = "HH:mm") LocalTime startTime) {
    }

    @Schema(description = "공연장 요약")
    public record VenueSummary(Long id, String name, String address) {
    }

    @Schema(description = "아티스트 요약")
    public record ArtistSummary(Long id, String name) {
    }

    public static AdminConcertDetailResponse from(Concert c) {
        return new AdminConcertDetailResponse(
                c.getId(), c.getKopisId(), c.getCategory(), c.getTitle(),
                List.copyOf(c.getTitleAliases()), c.getPosterUrl(), c.getStartDate(), c.getEndDate(),
                showtimesByDay(c),
                c.getLinkedVenue() == null ? null : new VenueSummary(
                        c.getLinkedVenue().getId(), c.getLinkedVenue().getName(), c.getLinkedVenue().getAddress()),
                c.getArtist() == null ? List.of() : List.of(new ArtistSummary(c.getArtist().getId(), c.getArtist().getName())),
                c.isPublished(), c.getPublishedAt(), c.getExpectedSongCount(),
                c.isLodgingVisible(), c.getLodgingUrl(), List.copyOf(c.getProductCodes()));
    }

    /** 기간의 DAY를 모두 채운다 - 시각이 저장되지 않은 DAY도 빠지지 않게(화면이 이 목록으로 입력칸을 그린다). */
    private static List<Showtime> showtimesByDay(Concert c) {
        if (c.getStartDate() == null || c.getEndDate() == null) return List.of();
        Map<LocalDate, LocalTime> saved = c.getShowtimes().stream()
                .filter(s -> s.getStartTime() != null)
                .collect(Collectors.toMap(ConcertShowtime::getDate, ConcertShowtime::getStartTime, (a, b) -> a));
        List<Showtime> result = new ArrayList<>();
        int day = 1;
        for (LocalDate date = c.getStartDate(); !date.isAfter(c.getEndDate()); date = date.plusDays(1)) {
            result.add(new Showtime(day++, date, saved.get(date)));
        }
        return result;
    }
}