package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.entity.ProductCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCountRepository extends JpaRepository<ProductCount, Long> {
}