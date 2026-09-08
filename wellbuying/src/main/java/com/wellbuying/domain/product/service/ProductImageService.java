package com.wellbuying.domain.product.service;

import com.wellbuying.domain.product.entity.ImageType;
import com.wellbuying.domain.product.entity.ProductImage;
import com.wellbuying.domain.product.event.ProductImageConfirmedEvent;
import com.wellbuying.domain.product.event.ProductImageOrphanedEvent;
import com.wellbuying.domain.product.repository.ProductImageRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductService productService;
    private final ProductImageUploadService productImageUploadService;
    private final ApplicationEventPublisher eventPublisher;

    public ProductImageService(ProductImageRepository productImageRepository, ProductService productService,
            ProductImageUploadService productImageUploadService, ApplicationEventPublisher eventPublisher) {
        this.productImageRepository = productImageRepository;
        this.productService = productService;
        this.productImageUploadService = productImageUploadService;
        this.eventPublisher = eventPublisher;
    }

    // 이미지 목록을 전체 교체 방식으로 저장 - 새로 추가된 URL은 확정 이벤트, 빠진 URL은 정리 이벤트를 발행하고
    // DB는 기존 것을 전부 지우고 요청받은 순서 그대로 다시 저장한다 (개별 diff 갱신보다 단순하고 안전함)
    @Transactional
    public void saveImages(Long sellerId, Long productId, ImageType imageType, List<String> imageUrls) {
        boolean hasExternalUrl = imageUrls.stream().anyMatch(url -> !productImageUploadService.isOurBucketUrl(url));
        if (hasExternalUrl) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Set<String> newUrls = new LinkedHashSet<>(imageUrls);
        if (newUrls.size() > imageType.getMaxCount()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        productService.getOwnedOrThrow(sellerId, productId);

        List<ProductImage> existing = productImageRepository.findByProductIdAndImageType(productId, imageType);
        Set<String> existingUrls = existing.stream().map(ProductImage::getImageUrl).collect(Collectors.toSet());

        productImageRepository.deleteByProductIdAndImageType(productId, imageType);
        List<ProductImage> toSave = new ArrayList<>();
        int order = 0;
        for (String url : newUrls) {
            toSave.add(ProductImage.register(productId, imageType, url, order++));
        }
        productImageRepository.saveAll(toSave);

        newUrls.stream()
                .filter(url -> !existingUrls.contains(url))
                .forEach(url -> eventPublisher.publishEvent(new ProductImageConfirmedEvent(url)));

        existingUrls.stream()
                .filter(url -> !newUrls.contains(url))
                .filter(productImageUploadService::isOurBucketUrl)
                .forEach(url -> eventPublisher.publishEvent(new ProductImageOrphanedEvent(url)));
    }
}
