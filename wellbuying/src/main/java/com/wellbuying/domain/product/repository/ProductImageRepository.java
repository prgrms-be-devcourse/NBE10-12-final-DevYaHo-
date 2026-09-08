package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.entity.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    // 상품 상세 조회 시 이미지 목록을 순서대로 가져올 때 사용
    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    // 상품 삭제 시 정리 이벤트 발행 대상을 조회할 때 사용
    List<ProductImage> findByProductId(Long productId);

    void deleteByProductId(Long productId);
}
