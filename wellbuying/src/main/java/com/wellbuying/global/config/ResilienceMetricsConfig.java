package com.wellbuying.global.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Resilience4j 서킷브레이커 지표를 Micrometer(Prometheus)에 연결한다.
// resilience4j-spring-boot3의 자동설정이 Spring Boot 4에서는 지표 바인딩을 해주지 않아 직접 등록.
// MeterBinder 빈은 Spring Boot가 MeterRegistry에 자동으로 바인딩하므로 bindTo 호출 불필요.
@Configuration
public class ResilienceMetricsConfig {

    @Bean
    public TaggedCircuitBreakerMetrics circuitBreakerMetrics(CircuitBreakerRegistry circuitBreakerRegistry) {
        return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(circuitBreakerRegistry);
    }
}
