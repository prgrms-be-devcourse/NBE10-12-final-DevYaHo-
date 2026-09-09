package com.wellbuying.domain.product.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.product.service.ProductImageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class ProductImageEventListenerTest {

    private static final String BUCKET = "wellbuying-dev";

    @Mock
    private S3Client s3Client;

    @Mock
    private ProductImageUploadService productImageUploadService;

    private ProductImageEventListener productImageEventListener;

    @BeforeEach
    void setUp() {
        productImageEventListener = new ProductImageEventListener(s3Client, productImageUploadService, BUCKET, 0L);
    }

    // 저장이 확정되면(상품 등록/수정 API로 저장 완료) pending 태그를 제거해 lifecycle rule의 자동 만료 대상에서 제외하는지 검증
    @Test
    void 저장이_확정되면_pending_태그를_제거한다() {
        String imageUrl = "https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/product-thumbnails/1/new.jpg";
        when(productImageUploadService.isOurBucketUrl(imageUrl)).thenReturn(true);
        when(productImageUploadService.extractKey(imageUrl)).thenReturn("product-thumbnails/1/new.jpg");

        productImageEventListener.handleConfirmed(new ProductImageConfirmedEvent(imageUrl));

        verify(s3Client).deleteObjectTagging(
                DeleteObjectTaggingRequest.builder().bucket(BUCKET).key("product-thumbnails/1/new.jpg").build());
    }

    // 우리 버킷 URL이면 더 이상 참조되지 않는 이전 이미지를 삭제하는지 검증
    @Test
    void 우리_버킷_URL이면_S3_객체를_삭제한다() {
        String imageUrl = "https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/product-thumbnails/1/old.jpg";
        when(productImageUploadService.isOurBucketUrl(imageUrl)).thenReturn(true);
        when(productImageUploadService.extractKey(imageUrl)).thenReturn("product-thumbnails/1/old.jpg");

        productImageEventListener.handleOrphaned(new ProductImageOrphanedEvent(imageUrl));

        verify(s3Client)
                .deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key("product-thumbnails/1/old.jpg").build());
    }

    // 외부 CDN URL이면 발행 측 필터링과 별개로 리스너에서도 삭제를 시도하지 않는지 검증
    @Test
    void 외부_CDN_URL이면_삭제를_시도하지_않는다() {
        String imageUrl = "https://k.kakaocdn.net/dn/product.jpg";
        when(productImageUploadService.isOurBucketUrl(imageUrl)).thenReturn(false);

        productImageEventListener.handleOrphaned(new ProductImageOrphanedEvent(imageUrl));

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    // 앞 2회 실패해도 3회째 성공하면 정상 종료된다
    @Test
    void handleConfirmed_일시_장애면_재시도해서_성공한다() {
        when(productImageUploadService.isOurBucketUrl("https://bucket-url/thumb.jpg")).thenReturn(true);
        when(productImageUploadService.extractKey("https://bucket-url/thumb.jpg")).thenReturn("thumb.jpg");
        when(s3Client.deleteObjectTagging(any(DeleteObjectTaggingRequest.class)))
                .thenThrow(new RuntimeException("s3 timeout"))
                .thenThrow(new RuntimeException("s3 timeout"))
                .thenReturn(null);

        productImageEventListener.handleConfirmed(new ProductImageConfirmedEvent("https://bucket-url/thumb.jpg"));

        verify(s3Client, times(3)).deleteObjectTagging(any(DeleteObjectTaggingRequest.class));
    }

    // 3회 모두 실패해도 예외를 밖으로 던지지 않고 종료된다
    @Test
    void handleConfirmed_3회_모두_실패해도_예외를_던지지_않는다() {
        when(productImageUploadService.isOurBucketUrl("https://bucket-url/thumb.jpg")).thenReturn(true);
        when(productImageUploadService.extractKey("https://bucket-url/thumb.jpg")).thenReturn("thumb.jpg");
        when(s3Client.deleteObjectTagging(any(DeleteObjectTaggingRequest.class)))
                .thenThrow(new RuntimeException("s3 down"));

        assertThatCode(() -> productImageEventListener.handleConfirmed(new ProductImageConfirmedEvent("https://bucket-url/thumb.jpg")))
                .doesNotThrowAnyException();

        verify(s3Client, times(3)).deleteObjectTagging(any(DeleteObjectTaggingRequest.class));
    }

    // 4xx 오류는 재시도해도 결과가 같으므로 1회 만에 종료된다
    @Test
    void handleConfirmed_4xx_오류면_재시도하지_않는다() {
        when(productImageUploadService.isOurBucketUrl("https://bucket-url/thumb.jpg")).thenReturn(true);
        when(productImageUploadService.extractKey("https://bucket-url/thumb.jpg")).thenReturn("thumb.jpg");
        when(s3Client.deleteObjectTagging(any(DeleteObjectTaggingRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("NoSuchKey").build());

        productImageEventListener.handleConfirmed(new ProductImageConfirmedEvent("https://bucket-url/thumb.jpg"));

        verify(s3Client, times(1)).deleteObjectTagging(any(DeleteObjectTaggingRequest.class));
    }
}
