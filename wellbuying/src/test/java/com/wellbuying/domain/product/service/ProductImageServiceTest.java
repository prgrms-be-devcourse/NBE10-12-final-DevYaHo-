package com.wellbuying.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.product.entity.ImageType;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductImage;
import com.wellbuying.domain.product.event.ProductImageConfirmedEvent;
import com.wellbuying.domain.product.event.ProductImageOrphanedEvent;
import com.wellbuying.domain.product.repository.ProductImageRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    private static final String OUR_URL_PREFIX = "https://wellbuying-dev.s3.ap-northeast-2.amazonaws.com/";

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ProductService productService;

    @Mock
    private ProductImageUploadService productImageUploadService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProductImageService productImageService;

    private ProductImageService service() {
        return new ProductImageService(productImageRepository, productService, productImageUploadService, eventPublisher);
    }

    // 요청 개수가 ImageType의 최대치를 초과하면 INVALID_INPUT 예외를 던지고 다른 로직은 실행하지 않는다
    @Test
    void 개수가_최대치를_초과하면_예외를_던진다() {
        productImageService = service();
        List<String> sevenUrls = List.of(
                OUR_URL_PREFIX + "1.jpg", OUR_URL_PREFIX + "2.jpg", OUR_URL_PREFIX + "3.jpg",
                OUR_URL_PREFIX + "4.jpg", OUR_URL_PREFIX + "5.jpg", OUR_URL_PREFIX + "6.jpg",
                OUR_URL_PREFIX + "7.jpg");
        when(productImageUploadService.isOurBucketUrl(any())).thenReturn(true);

        assertThatThrownBy(() -> productImageService.saveImages(1L, 10L, ImageType.GALLERY, sevenUrls))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(productService, never()).getOwnedOrThrow(anyLong(), anyLong());
    }

    // 중복 URL이 섞여 있으면 중복 제거 후 개수를 판단한다 - 중복 포함 7개지만 실제 고유값은 6개면 통과해야 함
    @Test
    void 중복_URL은_제거한_뒤_개수를_판단한다() {
        productImageService = service();
        String duplicated = OUR_URL_PREFIX + "1.jpg";
        List<String> urlsWithDuplicate = List.of(
                duplicated, duplicated, OUR_URL_PREFIX + "2.jpg", OUR_URL_PREFIX + "3.jpg",
                OUR_URL_PREFIX + "4.jpg", OUR_URL_PREFIX + "5.jpg", OUR_URL_PREFIX + "6.jpg");
        when(productImageUploadService.isOurBucketUrl(any())).thenReturn(true);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productService.getOwnedOrThrow(1L, 10L)).thenReturn(product);
        when(productImageRepository.findByProductIdAndImageType(10L, ImageType.GALLERY)).thenReturn(List.of());

        productImageService.saveImages(1L, 10L, ImageType.GALLERY, urlsWithDuplicate);

        ArgumentCaptor<List<ProductImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(productImageRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(6);
    }

    // 요청 URL 중 하나라도 우리 버킷 URL이 아니면 INVALID_INPUT 예외를 던진다
    @Test
    void 외부_URL이_섞여있으면_예외를_던진다() {
        productImageService = service();
        List<String> urls = List.of(OUR_URL_PREFIX + "1.jpg", "https://malicious.com/x.jpg");
        when(productImageUploadService.isOurBucketUrl(OUR_URL_PREFIX + "1.jpg")).thenReturn(true);
        when(productImageUploadService.isOurBucketUrl("https://malicious.com/x.jpg")).thenReturn(false);

        assertThatThrownBy(() -> productImageService.saveImages(1L, 10L, ImageType.GALLERY, urls))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(productService, never()).getOwnedOrThrow(anyLong(), anyLong());
    }

    // 소유하지 않은 상품이면 getOwnedOrThrow가 던지는 예외가 그대로 전파되고 이후 로직은 실행되지 않는다
    @Test
    void 소유하지_않은_상품이면_예외가_전파된다() {
        productImageService = service();
        List<String> urls = List.of(OUR_URL_PREFIX + "1.jpg");
        when(productImageUploadService.isOurBucketUrl(any())).thenReturn(true);
        when(productService.getOwnedOrThrow(1L, 10L))
                .thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        assertThatThrownBy(() -> productImageService.saveImages(1L, 10L, ImageType.GALLERY, urls))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verify(productImageRepository, never()).findByProductIdAndImageType(anyLong(), any());
    }

    // 기존 이미지가 없고 신규 URL만 있으면 확정 이벤트만 발행하고 정리 이벤트는 발행하지 않는다
    @Test
    void 신규_URL만_있으면_확정_이벤트만_발행한다() {
        productImageService = service();
        List<String> urls = List.of(OUR_URL_PREFIX + "new1.jpg", OUR_URL_PREFIX + "new2.jpg");
        when(productImageUploadService.isOurBucketUrl(any())).thenReturn(true);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productService.getOwnedOrThrow(1L, 10L)).thenReturn(product);
        when(productImageRepository.findByProductIdAndImageType(10L, ImageType.DESCRIPTION)).thenReturn(List.of());

        productImageService.saveImages(1L, 10L, ImageType.DESCRIPTION, urls);

        verify(eventPublisher, times(2)).publishEvent(any(ProductImageConfirmedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(ProductImageOrphanedEvent.class));
    }

    // 기존 이미지 전부가 새 리스트에서 빠지면 정리 이벤트만 발행하고 확정 이벤트는 발행하지 않는다
    @Test
    void 기존_URL이_전부_빠지면_정리_이벤트만_발행한다() {
        productImageService = service();
        ProductImage existingImage = ProductImage.register(10L, ImageType.GALLERY, OUR_URL_PREFIX + "old.jpg", 0);
        when(productImageUploadService.isOurBucketUrl(any())).thenReturn(true);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productService.getOwnedOrThrow(1L, 10L)).thenReturn(product);
        when(productImageRepository.findByProductIdAndImageType(10L, ImageType.GALLERY)).thenReturn(List.of(existingImage));

        productImageService.saveImages(1L, 10L, ImageType.GALLERY, List.of());

        verify(eventPublisher, never()).publishEvent(any(ProductImageConfirmedEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(ProductImageOrphanedEvent.class));
    }

    // 신규 추가와 기존 삭제가 섞이면 양쪽 이벤트가 각각 발행된다
    @Test
    void 신규_추가와_삭제가_섞이면_양쪽_이벤트를_각각_발행한다() {
        productImageService = service();
        ProductImage existingImage = ProductImage.register(10L, ImageType.GALLERY, OUR_URL_PREFIX + "old.jpg", 0);
        List<String> newUrls = List.of(OUR_URL_PREFIX + "new.jpg");
        when(productImageUploadService.isOurBucketUrl(any())).thenReturn(true);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productService.getOwnedOrThrow(1L, 10L)).thenReturn(product);
        when(productImageRepository.findByProductIdAndImageType(10L, ImageType.GALLERY)).thenReturn(List.of(existingImage));

        productImageService.saveImages(1L, 10L, ImageType.GALLERY, newUrls);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        List<Object> events = eventCaptor.getAllValues();

        assertThat(events).filteredOn(e -> e instanceof ProductImageConfirmedEvent)
                .extracting(e -> ((ProductImageConfirmedEvent) e).imageUrl())
                .containsExactly(OUR_URL_PREFIX + "new.jpg");
        assertThat(events).filteredOn(e -> e instanceof ProductImageOrphanedEvent)
                .extracting(e -> ((ProductImageOrphanedEvent) e).imageUrl())
                .containsExactly(OUR_URL_PREFIX + "old.jpg");
    }

    // URL 집합은 동일하고 순서만 바뀐 경우 이벤트를 전혀 발행하지 않는다 (신규/삭제 둘 다 없음)
    @Test
    void 순서만_바뀌면_이벤트를_발행하지_않는다() {
        productImageService = service();
        ProductImage existingA = ProductImage.register(10L, ImageType.GALLERY, OUR_URL_PREFIX + "a.jpg", 0);
        ProductImage existingB = ProductImage.register(10L, ImageType.GALLERY, OUR_URL_PREFIX + "b.jpg", 1);
        List<String> reordered = List.of(OUR_URL_PREFIX + "b.jpg", OUR_URL_PREFIX + "a.jpg");
        when(productImageUploadService.isOurBucketUrl(any())).thenReturn(true);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productService.getOwnedOrThrow(1L, 10L)).thenReturn(product);
        when(productImageRepository.findByProductIdAndImageType(10L, ImageType.GALLERY))
                .thenReturn(List.of(existingA, existingB));

        productImageService.saveImages(1L, 10L, ImageType.GALLERY, reordered);

        verify(eventPublisher, never()).publishEvent(any());
        ArgumentCaptor<List<ProductImage>> captor = ArgumentCaptor.forClass(List.class);
        verify(productImageRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(ProductImage::getImageUrl)
                .containsExactly(OUR_URL_PREFIX + "b.jpg", OUR_URL_PREFIX + "a.jpg");
        assertThat(captor.getValue()).extracting(ProductImage::getSortOrder).containsExactly(0, 1);
    }

    // 저장은 항상 해당 productId/imageType 기존 데이터를 지운 뒤 재생성한다 - 타입별 벌크 삭제 메서드 호출 검증
    @Test
    void 저장_전_기존_타입별_이미지를_삭제한다() {
        productImageService = service();
        List<String> urls = List.of(OUR_URL_PREFIX + "a.jpg");
        when(productImageUploadService.isOurBucketUrl(any())).thenReturn(true);
        Product product = Product.register(1L, 1L, "상품", "설명", 10000, "url");
        when(productService.getOwnedOrThrow(1L, 10L)).thenReturn(product);
        when(productImageRepository.findByProductIdAndImageType(10L, ImageType.DESCRIPTION)).thenReturn(List.of());

        productImageService.saveImages(1L, 10L, ImageType.DESCRIPTION, urls);

        verify(productImageRepository).deleteByProductIdAndImageType(10L, ImageType.DESCRIPTION);
    }
}
