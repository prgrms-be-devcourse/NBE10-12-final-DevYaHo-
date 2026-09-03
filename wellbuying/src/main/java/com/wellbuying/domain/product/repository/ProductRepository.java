package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductQueryRepository {

    List<Product> findBySellerIdOrderByIdDesc(Long sellerId);

    // 관리자 상품 심사 목록 조회용 - 상태별 조회
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
}