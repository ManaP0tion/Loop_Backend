package com.loop.loop_backend.Config;

import com.loop.loop_backend.Concert.domain.ConcertCategory;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import com.loop.loop_backend.Concert.dto.ConcertVenueDto;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 공연 목록 캐시는 Redis에 JSON으로 저장된다 - 다시 읽을 때 DTO가 그대로 복원되는지 검증한다.
class RedisConfigTest {

    private final RedisCacheConfiguration config =
            RedisConfig.concertListCacheConfig(Jackson2ObjectMapperBuilder.json().build());

    @Test
    @DisplayName("공연 목록 캐시 값은 저장 후 다시 읽어도 모든 필드가 같다")
    void concertListCache_WhenSerializedAndRead_RestoresSameDtos() {
        ConcertSummaryDto withVenue = ConcertSummaryDto.builder()
                .concertId(1L).artistId(12L).artistName("YUURI").kopisId("PF297519")
                .title("YUURI LIVE [서울]").posterUrl("https://img.example/p.jpg")
                .venue(new ConcertVenueDto("KSPO DOME", "서울 송파구", "1,050~2,040",
                        null, "https://map.kakao.com/x", null, 37.52, 127.12))
                .startDate(LocalDate.of(2026, 12, 5)).endDate(LocalDate.of(2026, 12, 6))
                .category(ConcertCategory.STAGE).categoryDisplayName(ConcertCategory.STAGE.getDisplayName())
                .preparing(true)
                .build();
        ConcertSummaryDto undated = ConcertSummaryDto.builder()
                .concertId(2L).title("미정 공연").category(ConcertCategory.STAGE).build();

        ByteBuffer stored = config.getValueSerializationPair().write(List.of(withVenue, undated));
        Object restored = config.getValueSerializationPair().read(stored);

        assertThat(restored).asInstanceOf(InstanceOfAssertFactories.list(ConcertSummaryDto.class))
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactly(withVenue, undated);
    }

    @Test
    @DisplayName("공연 목록 캐시는 TTL이 있고 null은 저장하지 않는다")
    void concertListCache_HasTtlAndRejectsNull() {
        assertThat(config.getTtlFunction().getTimeToLive(List.of(), List.of())).isEqualTo(RedisConfig.CONCERT_LIST_TTL);
        assertThat(config.getAllowCacheNullValues()).isFalse();
    }
}
