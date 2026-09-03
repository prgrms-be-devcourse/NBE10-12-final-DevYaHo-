package com.wellbuying;

import com.wellbuying.global.config.NoOpCacheTestConfig;
import java.util.stream.Stream;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

// 싱글턴 컨테이너 패턴: static 필드로 직접 start()해서 JVM 실행 동안 모든 서브클래스가 컨테이너 하나를 공유(afterAll에서 stop되지 않음)
@SpringBootTest
@Import(NoOpCacheTestConfig.class)
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    // CI에서 매 테스트마다 Nori 플러그인 이미지를 직접 빌드하면 Docker Hub pull이 실패할 수 있어,
    // .github/workflows/build-opensearch-test-image.yml로 미리 빌드해 ghcr.io에 올려둔 고정 태그를 사용한다
    private static final OpenSearchContainer<?> OPENSEARCH = new OpenSearchContainer<>(
            DockerImageName.parse("ghcr.io/prgrms-be-devcourse/wellbuying-opensearch-test:2.19.1-nori")
                    .asCompatibleSubstituteFor("opensearchproject/opensearch"));

    static {
        // 병렬로 기동: 한쪽이 실패해도 다른 컨테이너의 기동을 막지 않는다
        Startables.deepStart(Stream.of(POSTGRES, REDIS, KAFKA, OPENSEARCH)).join();
    }

    @DynamicPropertySource
    static void overrideDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("opensearch.uris", OPENSEARCH::getHttpHostAddress);
    }
}