package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.entity.ProductImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    // 상품 상세 조회 시 이미지 목록을 순서대로 가져올 때 사용
    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    // 상품 삭제 시 정리 이벤트 발행 대상을 조회할 때 사용
    List<ProductImage> findByProductId(Long productId);

    // 벌크 삭제 - S3 정리 이벤트 발행용 URL 목록은 findByProductId()로 먼저 조회한 뒤 호출할 것.
    // flushAutomatically: 같은 트랜잭션 내 미반영 변경사항을 먼저 flush해 벌크 쿼리와의 정합성 보장
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProductImage pi WHERE pi.productId = :productId")
    int deleteByProductId(@Param("productId") Long productId);
}
