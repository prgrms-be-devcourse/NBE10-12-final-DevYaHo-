package com.wellbuying.domain.member.service;

import com.wellbuying.domain.member.dto.ProfileImageUploadUrlRequest;
import com.wellbuying.domain.member.dto.ProfileImageUploadUrlResponse;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class ProfileImageUploadService {

    // SVG는 스크립트를 포함할 수 있어 제외 - 프로필 이미지 업로드 허용 목록 (contentType -> 확장자)
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String publicUrlPrefix;
    private final long presignedUrlExpirationSeconds;

    public ProfileImageUploadService(S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.region}") String region,
            @Value("${aws.s3.endpoint:}") String endpoint,
            @Value("${aws.s3.presigned-url-expiration-seconds}") long presignedUrlExpirationSeconds) {
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.presignedUrlExpirationSeconds = presignedUrlExpirationSeconds;
        // 설정값에 트레일링 슬래시가 섞여 들어와도 중복 슬래시(//)가 생기지 않도록 정규화
        String normalizedEndpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        // 운영은 virtual-hosted-style S3 URL, 로컬/CI(MinIO)는 endpoint override + path-style URL
        this.publicUrlPrefix = endpoint.isBlank()
                ? "https://%s.s3.%s.amazonaws.com/".formatted(bucket, region)
                : "%s/%s/".formatted(normalizedEndpoint, bucket);
    }

    // 허용된 contentType인지 확인 후 pending 태그가 포함된 presigned PUT URL을 발급 - 실제 저장 확정은 회원이 PATCH /api/members/me를 호출해야 이루어진다
    public ProfileImageUploadUrlResponse issueUploadUrl(Long memberId, ProfileImageUploadUrlRequest request) {
        String extension = ALLOWED_CONTENT_TYPES.get(request.contentType());
        if (extension == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String key = "profile-images/%d/%s.%s".formatted(memberId, UUID.randomUUID(), extension);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(request.contentType())
                .tagging("pending=true")
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignedUrlExpirationSeconds))
                .putObjectRequest(putObjectRequest)
                .build();
        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();

        return new ProfileImageUploadUrlResponse(uploadUrl, publicUrlPrefix + key);
    }

    // 우리 S3 버킷이 발급한 URL인지 확인 - OAuth 프로필 이미지(외부 CDN URL)는 정리 대상에서 제외하기 위함
    public boolean isOurBucketUrl(String url) {
        return url != null && url.startsWith(publicUrlPrefix);
    }

    // 우리 버킷 URL에서 S3 객체 키를 추출 - isOurBucketUrl()로 확인된 URL에만 사용할 것
    public String extractKey(String url) {
        return url.substring(publicUrlPrefix.length());
    }
}
