package com.wellbuying.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.product.dto.ProductDescriptionImageUploadUrlResponse;
import com.wellbuying.domain.product.dto.ProductImageUploadUrlRequest;
import com.wellbuying.domain.product.dto.ProductImageUploadUrlResponse;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@ExtendWith(MockitoExtension.class)
class ProductImageUploadServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedPutObjectRequest presignedRequest;

    private ProductImageUploadService productImageUploadService;

    @BeforeEach
    void setUp() {
        // endpoint를 빈 문자열로 주어 운영과 동일한 virtual-hosted-style 공개 URL 형식으로 검증
        productImageUploadService = new ProductImageUploadService(s3Presigner, "wellbuying-dev", "ap-northeast-2", "",
                300);
    }

    // 허용 목록(jpeg/png/webp)에 속한 contentType이면 presigned URL과 최종 공개 URL이 함께 발급되는지 검증
    @Test
    void 허용된_contentType이면_presigned_URL을_발급한다() throws MalformedURLException {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url())
                .thenReturn(new URL("https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/presigned"));

        ProductImageUploadUrlResponse response = productImageUploadService.issueUploadUrl(1L,
                new ProductImageUploadUrlRequest("image/jpeg"));

        assertThat(response.uploadUrl()).isEqualTo("https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/presigned");
        assertThat(response.thumbnailUrl())
                .startsWith("https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/product-thumbnails/1/")
                .endsWith(".jpg");
    }

    // SVG 등 허용 목록에 없는 contentType이면 INVALID_INPUT 예외가 발생하고 presigned URL을 발급하지 않는지 검증 (SVG는 스크립트 포함 가능성으로 명시적 제외)
    @Test
    void 허용되지_않은_contentType이면_예외가_발생한다() {
        assertThatThrownBy(() -> productImageUploadService.issueUploadUrl(1L,
                new ProductImageUploadUrlRequest("image/svg+xml")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    // 상세설명 이미지도 동일하게 허용 목록에 속하면 발급되는지, 키 prefix가 description-images/인지 검증
    @Test
    void 상세설명_이미지도_허용된_contentType이면_presigned_URL을_발급한다() throws MalformedURLException {
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url())
                .thenReturn(new URL("https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/presigned"));

        ProductDescriptionImageUploadUrlResponse response = productImageUploadService.issueDescriptionImageUploadUrl(1L,
                new ProductImageUploadUrlRequest("image/png"));

        assertThat(response.uploadUrl()).isEqualTo("https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/presigned");
        assertThat(response.imageUrl())
                .startsWith("https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/description-images/1/")
                .endsWith(".png");
    }

    // 상세설명 이미지 발급도 썸네일과 동일하게 허용되지 않은 contentType이면 예외가 발생하는지 검증
    @Test
    void 상세설명_이미지도_허용되지_않은_contentType이면_예외가_발생한다() {
        assertThatThrownBy(() -> productImageUploadService.issueDescriptionImageUploadUrl(1L,
                new ProductImageUploadUrlRequest("image/svg+xml")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(s3Presigner, never()).presignPutObject(any(PutObjectPresignRequest.class));
    }
}
