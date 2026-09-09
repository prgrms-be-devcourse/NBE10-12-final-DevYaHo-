package com.wellbuying;

import com.wellbuying.global.config.NoOpCacheTestConfig;
import java.net.URI;
import java.util.stream.Stream;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

// 싱글턴 컨테이너 패턴: static 필드로 직접 start()해서 JVM 실행 동안 모든 서브클래스가 컨테이너 하나를 공유(afterAll에서 stop되지 않음)
@SpringBootTest
@Import(NoOpCacheTestConfig.class)
public abstract class AbstractIntegrationTest {

    // 로컬(docker-compose)의 wellbuying-dev와 별개로, 테스트 전용 MinIO 컨테이너 안에 생성하는 버킷
    private static final String TEST_BUCKET = "wellbuying-test";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
            .withExposedPorts(6379);

    private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    // CI에서 매 테스트마다 Nori 플러그인 이미지를 직접 빌드하면 Docker Hub pull이 실패할 수 있어,
    // .github/workflows/build-opensearch-test-image.yml로 미리 빌드해 ghcr.io에 올려둔 고정 태그를 사용한다
    private static final OpenSearchContainer<?> OPENSEARCH = new OpenSearchContainer<>(
            DockerImageName.parse("ghcr.io/prgrms-be-devcourse/wellbuying-opensearch-test:2.19.1-nori")
                    .asCompatibleSubstituteFor("opensearchproject/opensearch"));

    // AWS 자격증명 없이도 presigned URL 발급/PUT/태깅을 실제로 검증하기 위한 S3 호환 스토리지 (phase18)
    private static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest");

    static {
        // 병렬로 기동: 한쪽이 실패해도 다른 컨테이너의 기동을 막지 않는다
        Startables.deepStart(Stream.of(POSTGRES, REDIS, KAFKA, OPENSEARCH, MINIO)).join();
        createTestBucket();
    }

    // MinIO는 버킷을 미리 만들어주지 않으므로, 애플리케이션이 쓸 버킷을 컨테이너 기동 직후 1회 생성
    private static void createTestBucket() {
        try (S3Client s3Client = S3Client.builder()
                .region(Region.AP_NORTHEAST_2)
                .endpointOverride(URI.create(MINIO.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build());
        }
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
        registry.add("aws.s3.endpoint", MINIO::getS3URL);
        registry.add("aws.s3.access-key", MINIO::getUserName);
        registry.add("aws.s3.secret-key", MINIO::getPassword);
        registry.add("aws.s3.bucket", () -> TEST_BUCKET);
        // application-local.yaml의 cookie-secure=false(브라우저 수동 테스트용 오버라이드)는 무시하고
        // 운영 동작(Secure 쿠키)을 기준으로 검증/문서화한다
        registry.add("jwt.cookie-secure", () -> true);
    }
}