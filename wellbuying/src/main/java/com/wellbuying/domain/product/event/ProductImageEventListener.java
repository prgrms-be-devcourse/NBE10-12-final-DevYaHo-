package com.wellbuying.domain.product.event;

import com.wellbuying.domain.product.service.ProductImageUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

// S3 정리 작업 중 handleOrphaned(삭제)는 best-effort - 실패해도 에러 로그만 남긴다 (비용 문제일 뿐 유실 아님)
// handleConfirmed(태그 제거)는 실패 시 lifecycle rule로 24시간 뒤 이미지가 영구 삭제되므로 재시도한다
// (§ 6-1/§ 6-2, phase17 메일 리스너와 달리 삭제 실패는 수동 정리가 필요할 수 있음)
// 발행 측(ProductService)이 이미 isOurBucketUrl로 걸러서 이벤트를 발행하지만, S3를 실제로 변경하는
// 이 리스너에서도 한 번 더 확인해 외부 CDN URL 등을 절대 건드리지 않도록 한다
@Component
public class ProductImageEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductImageEventListener.class);

    private static final int MAX_CONFIRM_ATTEMPTS = 3;

    private final S3Client s3Client;
    private final ProductImageUploadService productImageUploadService;
    private final String bucket;
    private final long confirmRetryDelayMs;

    public ProductImageEventListener(S3Client s3Client, ProductImageUploadService productImageUploadService,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.confirm-retry-delay-ms:1000}") long confirmRetryDelayMs) {
        this.s3Client = s3Client;
        this.productImageUploadService = productImageUploadService;
        this.bucket = bucket;
        this.confirmRetryDelayMs = confirmRetryDelayMs;
    }

    // 신규 업로드가 상품 등록/수정으로 저장 확정되면 pending 태그를 제거해 lifecycle rule의 24시간 자동 만료 대상에서 제외
    @Async("s3ConfirmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleConfirmed(ProductImageConfirmedEvent event) {
        if (!productImageUploadService.isOurBucketUrl(event.imageUrl())) {
            return;
        }
        String key = productImageUploadService.extractKey(event.imageUrl());
        for (int attempt = 1; attempt <= MAX_CONFIRM_ATTEMPTS; attempt++) {
            try {
                s3Client.deleteObjectTagging(DeleteObjectTaggingRequest.builder().bucket(bucket).key(key).build());
                return;
            } catch (Exception e) {
                if (!isRetryable(e)) {
                    log.error("상품 이미지 pending 태그 제거 실패 (재시도 불가 오류) - 수동 확인 필요: key={}", key, e);
                    return;
                }
                if (attempt == MAX_CONFIRM_ATTEMPTS) {
                    log.error("상품 이미지 pending 태그 제거 최종 실패 - 24시간 내 수동 확인 필요: key={}, attempts={}",
                            key, attempt, e);
                    return;
                }
                log.warn("상품 이미지 pending 태그 제거 실패, 재시도 예정: key={}, attempt={}/{}",
                        key, attempt, MAX_CONFIRM_ATTEMPTS, e);
                if (!sleepBeforeRetry(confirmRetryDelayMs * attempt)) {
                    return;
                }
            }
        }
    }

    // 상품 이미지 교체/삭제로 더 이상 참조되지 않는 이전 이미지를 삭제
    @Async("s3ConfirmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrphaned(ProductImageOrphanedEvent event) {
        if (!productImageUploadService.isOurBucketUrl(event.imageUrl())) {
            return;
        }
        String key = productImageUploadService.extractKey(event.imageUrl());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.error("상품 이미지 삭제 실패: key={}", key, e);
        }
    }

    // 재시도 대상: S3 5xx, 스로틀링(429/SlowDown), 네트워크 타임아웃 등 SDK 통신 오류.
    // 4xx(NoSuchKey, AccessDenied 등)와 코드 버그(NPE 등)는 재시도해도 결과가 같으므로 즉시 중단
    private boolean isRetryable(Exception e) {
        if (e instanceof S3Exception s3Exception) {
            return s3Exception.isThrottlingException() || s3Exception.statusCode() >= 500;
        }
        return e instanceof SdkException;
    }

    // 재시도 대기. 인터럽트되면 false를 반환해 재시도를 중단한다
    private boolean sleepBeforeRetry(long millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}