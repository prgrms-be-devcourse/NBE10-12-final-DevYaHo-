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
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectTaggingRequest;

// S3 정리 작업은 best-effort - 실패해도 재시도/보상 트랜잭션 없이 에러 로그만 남긴다
// (§ 6-1/§ 6-2, phase17 메일 리스너와 달리 삭제 실패는 수동 정리가 필요할 수 있음)
// 발행 측(ProductService)이 이미 isOurBucketUrl로 걸러서 이벤트를 발행하지만, S3를 실제로 변경하는
// 이 리스너에서도 한 번 더 확인해 외부 CDN URL 등을 절대 건드리지 않도록 한다
@Component
public class ProductImageEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductImageEventListener.class);

    private final S3Client s3Client;
    private final ProductImageUploadService productImageUploadService;
    private final String bucket;

    public ProductImageEventListener(S3Client s3Client, ProductImageUploadService productImageUploadService,
            @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.productImageUploadService = productImageUploadService;
        this.bucket = bucket;
    }

    // 신규 업로드가 상품 등록/수정으로 저장 확정되면 pending 태그를 제거해 lifecycle rule의 24시간 자동 만료 대상에서 제외
    @Async("s3ConfirmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleConfirmed(ProductImageConfirmedEvent event) {
        if (!productImageUploadService.isOurBucketUrl(event.imageUrl())) {
            return;
        }
        String key = productImageUploadService.extractKey(event.imageUrl());
        try {
            s3Client.deleteObjectTagging(DeleteObjectTaggingRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.error("상품 이미지 pending 태그 제거 실패: key={}", key, e);
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
}