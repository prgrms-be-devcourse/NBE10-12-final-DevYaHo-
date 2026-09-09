package com.wellbuying.global.config;

import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
@EnableCaching
public class CacheConfig {

    // 기본 직렬화(JDK Serialization)는 record 타입(CategoryTreeResponse 등)을 캐싱하지 못해서
    // JSON 직렬화로 교체 - 앞으로 다른 record DTO를 캐싱해도 같은 문제가 재발하지 않음
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        // 조회수 기반 인기 상품은 계속 바뀌는 데이터라 기본 24시간 TTL 대신 짧은 주기로 갱신
        RedisCacheConfiguration popularProductsConfig = defaultConfig.entryTtl(Duration.ofMinutes(10));
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("popularProducts", popularProductsConfig)
                .build();
    }
}