package com.wellbuying.domain.product.service;

import com.wellbuying.domain.product.dto.ProductDescriptionImageUploadUrlResponse;
import com.wellbuying.domain.product.dto.ProductImageUploadUrlRequest;
import com.wellbuying.domain.product.dto.ProductImageUploadUrlResponse;
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
public class ProductImageUploadService {

    // SVG는 스크립트를 포함할 수 있어 제외 - 상품 썸네일 업로드 허용 목록 (contentType -> 확장자)
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String publicUrlPrefix;
    private final long presignedUrlExpirationSeconds;

    public ProductImageUploadService(S3Presigner s3Presigner,
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

    // 허용된 contentType인지 확인 후 pending 태그가 포함된 presigned PUT URL을 발급 - 실제 저장 확정은 판매자가 상품 등록/수정 API를 호출해야 이루어진다
    public ProductImageUploadUrlResponse issueUploadUrl(Long sellerId, ProductImageUploadUrlRequest request) {
        String extension = validateContentType(request.contentType());
        String key = "product-thumbnails/%d/%s.%s".formatted(sellerId, UUID.randomUUID(), extension);
        String uploadUrl = issuePresignedPutUrl(key, request.contentType());
        return new ProductImageUploadUrlResponse(uploadUrl, publicUrlPrefix + key);
    }

    // 상세설명 이미지 발급 - 발급 시점엔 productId가 없어도 되므로(등록 전/후 모두 호출 가능)
    // sellerId 기준으로 키를 생성하고, 실제 상품에 붙이는 건 별도 저장(확정) API가 담당한다
    public ProductDescriptionImageUploadUrlResponse issueDescriptionImageUploadUrl(Long sellerId,
            ProductImageUploadUrlRequest request) {
        String extension = validateContentType(request.contentType());
        String key = "description-images/%d/%s.%s".formatted(sellerId, UUID.randomUUID(), extension);
        String uploadUrl = issuePresignedPutUrl(key, request.contentType());
        return new ProductDescriptionImageUploadUrlResponse(uploadUrl, publicUrlPrefix + key);
    }

    // 우리 S3 버킷이 발급한 URL인지 확인 - 외부 CDN URL은 정리 대상에서 제외하기 위함
    public boolean isOurBucketUrl(String url) {
        return url != null && url.startsWith(publicUrlPrefix);
    }

    // 우리 버킷 URL에서 S3 객체 키를 추출 - isOurBucketUrl()로 확인된 URL에만 사용할 것
    public String extractKey(String url) {
        return url.substring(publicUrlPrefix.length());
    }

    private String validateContentType(String contentType) {
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return extension;
    }

    private String issuePresignedPutUrl(String key, String contentType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .tagging("pending=true")
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(presignedUrlExpirationSeconds))
                .putObjectRequest(putObjectRequest)
                .build();
        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }
}