package com.wellbuying.domain.product.service;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.product.dto.ProductAdminResponse;
import com.wellbuying.domain.product.dto.ProductCreateRequest;
import com.wellbuying.domain.product.dto.ProductUpdateRequest;
import com.wellbuying.domain.product.dto.ProductDetailResponse;
import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCount;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductCountRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.domain.product.search.ProductSearchEventOutbox;
import com.wellbuying.domain.product.search.ProductSearchEventOutboxRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.global.dto.CursorPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductCountRepository productCountRepository;
    private final ProductSearchEventOutboxRepository outboxRepository;

    public ProductService(ProductRepository productRepository, MemberRepository memberRepository,
                          ProductCategoryRepository productCategoryRepository,
                          ProductCountRepository productCountRepository,
                          ProductSearchEventOutboxRepository outboxRepository) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productCountRepository = productCountRepository;
        this.outboxRepository = outboxRepository;
    }

    // 카테고리/가격 필터와 정렬 조건에 맞는 상품 목록을 커서 기반으로 조회
    @Transactional(readOnly = true)
    public CursorPageResponse<ProductSummaryResponse> getProducts(ProductSearchCondition condition, String cursor, int size) {
        return productRepository.search(condition, cursor, size);
    }

    // 공동구매 상세 화면에서 상품 설명/썸네일 등을 보여주기 위해 단건 조회
    @Transactional(readOnly = true)
    public ProductDetailResponse getDetail(Long productId) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductDetailResponse.of(product);
    }

    // 공동구매 생성 시 사용 - 상품이 존재하고 요청한 판매자 소유일 때만 반환, 아니면 존재 여부를 노출하지 않고 동일한 예외로 처리
    @Transactional(readOnly = true)
    public Product getOwnedOrThrow(Long sellerId, Long productId) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    // 생산자(SELLER)만 상품을 등록할 수 있음
    @Transactional
    public Long createProduct(Long sellerId, ProductCreateRequest request) {
        // 판매자 판단을 @PreAuthorize(JWT 기반)가 아닌 DB 재조회로 하는 이유:
        // 판매자 승인(SellerInfoService.approve()) 직후 발급된 새 토큰을 아직 안 받은 상태로
        // 바로 상품을 등록하려는 경우, JWT 속 role은 여전히 예전 값(BUYER)일 수 있음.
        // MemberRepository.findByIdAndDeletedAtIsNull이 "토큰 재발급 시 최신 role 확인용"으로
        // 이미 쓰이고 있는 것과 같은 이유로, 여기서도 DB 기준 최신 role을 확인함.
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
        Long productId = productRepository.save(product).getId();
        productCountRepository.save(ProductCount.init(productId));
        return productId;
    }

    // 로그인한 판매자 본인이 등록한 상품 전체(상태 무관, 삭제된 상품은 제외) 조회
    @Transactional(readOnly = true)
    public Slice<ProductMineResponse> getMyProducts(Long sellerId, Pageable pageable) {
        return productRepository.findBySeller(sellerId, pageable);
    }

    // 관리자 상품 심사 목록 - 상태별(PENDING/APPROVED/REJECTED) 조회
    @Transactional(readOnly = true)
    public Page<ProductAdminResponse> findByStatus(ProductStatus status, Pageable pageable) {
        return productRepository.findByStatusAndDeletedAtIsNull(status, pageable).map(ProductAdminResponse::of);
    }

    // 상품 승인 - PENDING 여부 검증은 Product.approve()가 이미 담당(PRODUCT_ALREADY_PROCESSED)
    @Transactional
    public void approve(Long productId) {
        findProduct(productId).approve();
        outboxRepository.save(ProductSearchEventOutbox.upsert(productId));
    }

    // 상품 거절 - PENDING 여부 검증은 Product.reject()가 이미 담당(PRODUCT_ALREADY_PROCESSED)
    @Transactional
    public void reject(Long productId) {
        findProduct(productId).reject();
    }

    @Transactional
    public void updateProduct(Long sellerId, Long productId, ProductUpdateRequest request) {
        Product product = getOwnedOrThrow(sellerId, productId);
        if (!productCategoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        product.update(request.categoryId(), request.productName(), request.description(),
                request.startPrice(), request.thumbnailUrl());
        if (product.getStatus() == ProductStatus.APPROVED) {
            outboxRepository.save(ProductSearchEventOutbox.upsert(productId));
        }
    }

    @Transactional
    public void deleteProduct(Long sellerId, Long productId) {
        Product product = getOwnedOrThrow(sellerId, productId);
        boolean wasIndexed = product.getStatus() == ProductStatus.APPROVED;
        product.delete();
        if (wasIndexed) {
            outboxRepository.save(ProductSearchEventOutbox.delete(productId));
        }
    }

    private Product findProduct(Long productId) {
        return productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
