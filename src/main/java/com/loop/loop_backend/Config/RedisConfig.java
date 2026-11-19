package com.loop.loop_backend.Config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loop.loop_backend.Concert.dto.ConcertSummaryDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class RedisConfig {

    // 공연 탭 목록(section + period). 사용자와 상관없이 결과가 같아서 공유 캐시로 둔다.
    public static final String CONCERT_LIST_CACHE = "concertList";

    // 어드민 공연 등록·수정·삭제는 바로 지운다. 그 밖의 변경(KOPIS 승인, 아티스트·공연장 수정 등)은 TTL만큼 늦게 보인다.
    static final Duration CONCERT_LIST_TTL = Duration.ofSeconds(30);

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Bean
    public RedisConnectionFactory redisConnectionFactory(){
        return new LettuceConnectionFactory(host, port);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        return new StringRedisTemplate(redisConnectionFactory());
    }

    // transactionAware: @CacheEvict를 커밋 뒤로 미룬다. 커밋 전에 지우면 그 사이 조회가 옛 값을 다시 채울 수 있다.
    @Bean
    public RedisCacheManager cacheManager(ObjectMapper objectMapper) {
        return RedisCacheManager.builder(redisConnectionFactory())
                .withCacheConfiguration(CONCERT_LIST_CACHE, concertListCacheConfig(objectMapper))
                .transactionAware()
                .build();
    }

    // 타입을 고정한 직렬화기 - 값에 클래스명을 넣지 않아 DTO 패키지를 옮겨도 캐시가 깨지지 않는다.
    static RedisCacheConfiguration concertListCacheConfig(ObjectMapper objectMapper) {
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, ConcertSummaryDto.class);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(CONCERT_LIST_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, type)));
    }
}
