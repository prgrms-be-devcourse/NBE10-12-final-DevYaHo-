package com.wellbuying.global.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// RestClient.Builder 빈에 connect/read timeout을 건다 - 미설정 시 무기한 대기하는 문제 방지
// (09-pg-timeout-retry.md). 현재 RestClient.Builder를 주입받는 곳은 TossPaymentGateway/
// TossBillingKeyClient뿐이라 사실상 토스 API 호출에만 적용된다.
//
// RestClient.Builder를 직접 만드는 대신 RestClientCustomizer로 등록하는 이유: 이 커스터마이저는
// Spring이 주입하는 RestClient.Builder 빈(RestClientAutoConfiguration)에만 자동 적용되고, 테스트가
// MockRestServiceServer로 직접 바인딩한 별도 builder(DI를 거치지 않음)는 건드리지 않는다 -
// 거기에 다시 requestFactory를 덮어쓰면 목 서버가 무력화된다 (TossPaymentGatewayTest 등)
@Configuration
public class TossRestClientConfig {

    @Bean
    public RestClientCustomizer tossRestClientCustomizer(
            @Value("${toss.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${toss.read-timeout-ms:10000}") long readTimeoutMs) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs));
        return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}
