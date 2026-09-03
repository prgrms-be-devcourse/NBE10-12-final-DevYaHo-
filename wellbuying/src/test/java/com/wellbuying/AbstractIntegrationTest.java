package com.wellbuying;

import java.nio.file.Path;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

// 싱글턴 컨테이너 패턴: @Container 없이 static 필드로 직접 start()해서 JVM 실행 동안 모든 서브클래스가 컨테이너 하나를 공유(afterAll에서 stop되지 않음)
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // docker/opensearch/Dockerfile로 빌드한 커스텀 이미지(Nori 한글 분석기 포함)를 사용 - 공식 이미지는 Nori가 없어 한글 검색 테스트가 깨진다
    private static final ImageFromDockerfile OPENSEARCH_IMAGE = new ImageFromDockerfile("wellbuying-opensearch-test", false)
            .withFileFromPath(".", Path.of("docker/opensearch"));

    private static final OpenSearchContainer<?> OPENSEARCH = new OpenSearchContainer<>(
            DockerImageName.parse(OPENSEARCH_IMAGE.get()).asCompatibleSubstituteFor("opensearchproject/opensearch"));

    static {
        POSTGRES.start();
        OPENSEARCH.start();
    }

    @DynamicPropertySource
    static void overrideDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("opensearch.uris", OPENSEARCH::getHttpHostAddress);
    }
}
