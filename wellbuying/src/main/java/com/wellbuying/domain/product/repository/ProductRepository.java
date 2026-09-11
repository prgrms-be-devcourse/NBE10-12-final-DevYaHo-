package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductQueryRepository {

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    List<Product> findByIdInAndDeletedAtIsNull(List<Long> ids);

    List<Product> findBySellerIdOrderByIdDesc(Long sellerId);

    // 관리자 상품 심사 목록 조회용 - 상태별 조회 (소프트 삭제된 상품 제외)
    Page<Product> findByStatusAndDeletedAtIsNull(ProductStatus status, Pageable pageable);

    // 관리자 상품 키워드 검색용 (상품명 부분 일치)
    Page<Product> findByStatusAndDeletedAtIsNullAndProductNameContainingIgnoreCase(
            ProductStatus status, String keyword, Pageable pageable);

    // 검색 인덱스 보정 배치용 - id 커서 기반 순차 조회 (count 쿼리 없음, OFFSET 없음)
    List<Product> findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(
            ProductStatus status, Long lastId, Pageable pageable);
    // 관리자 삭제 이력 조회용 - 소프트 삭제된 상품만 조회
    Page<Product> findByDeletedAtIsNotNull(Pageable pageable);

    // 카테고리 삭제 차단용 - 해당 카테고리를 참조하는 상품 존재 여부 확인
    boolean existsByCategoryIdAndDeletedAtIsNull(Long categoryId);
}