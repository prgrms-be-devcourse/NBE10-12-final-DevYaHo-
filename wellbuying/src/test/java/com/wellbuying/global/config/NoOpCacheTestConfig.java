package com.wellbuying.global.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class NoOpCacheTestConfig {

    @Bean
    @Primary
    public CacheManager noOpCacheManager() {
        return new NoOpCacheManager();
    }
}
