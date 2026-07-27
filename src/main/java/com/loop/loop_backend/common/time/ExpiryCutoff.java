package com.loop.loop_backend.common.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

// 콘서트/동행글 등 "기준일이 지나면 노출을 끊는" 화면들이 공통으로 쓰는 컷오프 규칙.
// 기준일 다음날 오전 10시까지는 노출하고, 그 이후부터 만료 처리한다.
public final class ExpiryCutoff {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime RESET_TIME = LocalTime.of(10, 0);

    private ExpiryCutoff() {
    }

    // 반환값 이상의 기준일(concert.endDate, 관람일 등)은 아직 노출 대상이라는 뜻.
    public static LocalDate cutoffDate() {
        return cutoffDate(LocalDateTime.now(ZONE_KST));
    }

    // 시각을 직접 넣어 오전 10시 경계를 결정적으로 테스트하기 위한 오버로드.
    public static LocalDate cutoffDate(LocalDateTime now) {
        return now.toLocalTime().isBefore(RESET_TIME)
                ? now.toLocalDate().minusDays(1)
                : now.toLocalDate();
    }
}