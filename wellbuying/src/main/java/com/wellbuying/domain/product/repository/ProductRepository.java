package com.wellbuying.domain.product.repository;

import com.wellbuying.domain.product.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductQueryRepository {

    List<Product> findBySellerIdOrderByIdDesc(Long sellerId);
}