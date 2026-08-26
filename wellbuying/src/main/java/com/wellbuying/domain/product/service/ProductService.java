package com.wellbuying.domain.product.service;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.product.dto.ProductCreateRequest;
import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final ProductCategoryRepository productCategoryRepository;

    public ProductService(ProductRepository productRepository, MemberRepository memberRepository,
            ProductCategoryRepository productCategoryRepository) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.productCategoryRepository = productCategoryRepository;
    }

    // 카테고리/가격 필터와 정렬 조건에 맞는 상품 목록을 페이지 단위로 조회
    @Transactional(readOnly = true)
    public Slice<ProductSummaryResponse> getProducts(ProductSearchCondition condition, Pageable pageable) {
        return productRepository.search(condition, pageable);
    }

    // 생산자(SELLER)만 상품을 등록할 수 있음
    @Transactional
    public Long createProduct(Long sellerId, ProductCreateRequest request) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getRole() != Role.SELLER) {
            throw new BusinessException(ErrorCode.PRODUCT_FORBIDDEN);
        }
        if (!productCategoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        Product product = Product.register(sellerId, request.categoryId(), request.productName(),
                request.description(), request.startPrice(), request.thumbnailUrl());
        return productRepository.save(product).getId();
    }

    // 로그인한 판매자 본인이 등록한 상품 전체(상태 무관) 조회
    @Transactional(readOnly = true)
    public Slice<ProductMineResponse> getMyProducts(Long sellerId, Pageable pageable) {
        return productRepository.findBySeller(sellerId, pageable);
    }
}