package com.wellbuying.domain.product.service;

import com.wellbuying.domain.admin.dto.AdminActionLogResponse;
import com.wellbuying.domain.admin.entity.AdminActionLog;
import com.wellbuying.domain.admin.entity.AdminActionTargetType;
import com.wellbuying.domain.admin.entity.AdminActionType;
import com.wellbuying.domain.admin.repository.AdminActionLogRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.product.dto.ProductAdminResponse;
import com.wellbuying.domain.product.dto.ProductDeletedAdminResponse;
import com.wellbuying.domain.product.dto.ProductCreateRequest;
import com.wellbuying.domain.product.dto.ProductUpdateRequest;
import com.wellbuying.domain.product.dto.ProductDetailResponse;
import com.wellbuying.domain.product.dto.ProductMineResponse;
import com.wellbuying.domain.product.dto.ProductSearchCondition;
import com.wellbuying.domain.product.dto.ProductSummaryResponse;
import com.wellbuying.domain.product.entity.ImageType;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCount;
import com.wellbuying.domain.product.entity.ProductImage;
import com.wellbuying.domain.product.entity.ProductStatus;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductImageRepository;
import com.wellbuying.domain.product.repository.ProductCountRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.domain.product.search.ProductSearchEventOutbox;
import com.wellbuying.domain.product.search.ProductSearchEventOutboxRepository;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.product.event.ProductImageConfirmedEvent;
import com.wellbuying.domain.product.event.ProductImageOrphanedEvent;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.global.dto.CursorPageResponse;
import org.springframework.context.ApplicationEventPublisher;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
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
    private final GroupBuyRepository groupBuyRepository;
    private final AdminActionLogRepository adminActionLogRepository;
    private final ProductImageUploadService productImageUploadService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProductImageRepository productImageRepository;

    public ProductService(ProductRepository productRepository, MemberRepository memberRepository,
                          ProductCategoryRepository productCategoryRepository,
                          ProductCountRepository productCountRepository,
                          ProductSearchEventOutboxRepository outboxRepository,
                          GroupBuyRepository groupBuyRepository,
                          AdminActionLogRepository adminActionLogRepository,
                          ProductImageUploadService productImageUploadService,
                          ApplicationEventPublisher eventPublisher,
                          ProductImageRepository productImageRepository) {
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productCountRepository = productCountRepository;
        this.outboxRepository = outboxRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.adminActionLogRepository = adminActionLogRepository;
        this.productImageUploadService = productImageUploadService;
        this.eventPublisher = eventPublisher;
        this.productImageRepository = productImageRepository;
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
        Map<ImageType, List<String>> imageUrlsByType = productImageRepository
                .findByProductIdOrderBySortOrderAsc(productId).stream()
                .collect(Collectors.groupingBy(ProductImage::getImageType,
                        Collectors.mapping(ProductImage::getImageUrl, Collectors.toList())));
        List<String> galleryImageUrls = imageUrlsByType.getOrDefault(ImageType.GALLERY, List.of());
        List<String> descriptionImageUrls = imageUrlsByType.getOrDefault(ImageType.DESCRIPTION, List.of());
        return ProductDetailResponse.of(product, galleryImageUrls, descriptionImageUrls);
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
        if (productImageUploadService.isOurBucketUrl(request.thumbnailUrl())) {
            eventPublisher.publishEvent(new ProductImageConfirmedEvent(request.thumbnailUrl()));
        }
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
    public void approve(Long productId, Long adminId, String reason) {
        findProduct(productId).approve();
        outboxRepository.save(ProductSearchEventOutbox.upsert(productId));
        recordAction(productId, adminId, AdminActionType.APPROVE, reason);
    }

    // 상품 거절 - PENDING 여부 검증은 Product.reject()가 이미 담당(PRODUCT_ALREADY_PROCESSED)
    @Transactional
    public void reject(Long productId, Long adminId, String reason) {
        findProduct(productId).reject();
        recordAction(productId, adminId, AdminActionType.REJECT, reason);
    }

    // 상품 승인/거절 이력 조회 - "승인 대기 요청 처리" 화면에서 사용
    @Transactional(readOnly = true)
    public Page<AdminActionLogResponse> listActionLogs(Pageable pageable) {
        Page<AdminActionLog> page = adminActionLogRepository
                .findAllByTargetTypeOrderByOccurredAtDesc(AdminActionTargetType.PRODUCT, pageable);
        List<Long> productIds = page.getContent().stream().map(AdminActionLog::getTargetId).distinct().toList();
        Map<Long, String> productNamesById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getProductName));
        List<Long> adminIds = page.getContent().stream().map(AdminActionLog::getAdminId).distinct().toList();
        Map<Long, String> adminNamesById = memberRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(Member::getId, Member::getName));
        return page.map(actionLog -> AdminActionLogResponse.of(actionLog,
                productNamesById.getOrDefault(actionLog.getTargetId(), ""),
                adminNamesById.getOrDefault(actionLog.getAdminId(), "")));
    }

    private void recordAction(Long productId, Long adminId, AdminActionType action, String reason) {
        adminActionLogRepository.save(
                AdminActionLog.record(AdminActionTargetType.PRODUCT, productId, adminId, action, reason));
    }

    @Transactional
    public void updateProduct(Long sellerId, Long productId, ProductUpdateRequest request) {
        Product product = getOwnedOrThrow(sellerId, productId);
        if (!productCategoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        String previousThumbnailUrl = product.getThumbnailUrl();
        product.update(request.categoryId(), request.productName(), request.description(),
                request.startPrice(), request.thumbnailUrl());
        if (product.getStatus() == ProductStatus.APPROVED) {
            outboxRepository.save(ProductSearchEventOutbox.upsert(productId));
        }
        String newThumbnailUrl = request.thumbnailUrl();
        boolean imageChanged = !Objects.equals(newThumbnailUrl, previousThumbnailUrl);
        if (imageChanged && productImageUploadService.isOurBucketUrl(newThumbnailUrl)) {
            eventPublisher.publishEvent(new ProductImageConfirmedEvent(newThumbnailUrl));
        }
        if (imageChanged && productImageUploadService.isOurBucketUrl(previousThumbnailUrl)) {
            eventPublisher.publishEvent(new ProductImageOrphanedEvent(previousThumbnailUrl));
        }
    }

    @Transactional
    public void deleteProduct(Long sellerId, Long productId, String reason) {
        Product product = getOwnedOrThrow(sellerId, productId);
        validateNoActiveGroupBuy(productId);
        boolean wasIndexed = product.getStatus() == ProductStatus.APPROVED;
        String thumbnailUrl = product.getThumbnailUrl();
        product.delete(sellerId, reason);
        if (wasIndexed) {
            outboxRepository.save(ProductSearchEventOutbox.delete(productId));
        }
        if (productImageUploadService.isOurBucketUrl(thumbnailUrl)) {
            eventPublisher.publishEvent(new ProductImageOrphanedEvent(thumbnailUrl));
        }
        List<ProductImage> extraImages = productImageRepository.findByProductId(productId);
        extraImages.stream()
                .map(ProductImage::getImageUrl)
                .filter(productImageUploadService::isOurBucketUrl)
                .forEach(url -> eventPublisher.publishEvent(new ProductImageOrphanedEvent(url)));
        productImageRepository.deleteByProductId(productId);
    }

    // 삭제 이력 조회 - deletedAt이 있는 상품만, 최근 삭제순 정렬은 컨트롤러 Pageable로 처리
    @Transactional(readOnly = true)
    public Page<ProductDeletedAdminResponse> findDeleted(Pageable pageable) {
        return productRepository.findByDeletedAtIsNotNull(pageable).map(ProductDeletedAdminResponse::of);
    }

    // 관리자 강제 삭제 - 소유권 무관, 사유 필수, 공동구매 진행 중이면 동일하게 차단
    @Transactional
    public void adminDeleteProduct(Long adminId, Long productId, String reason) {
        Product product = findProduct(productId);
        validateNoActiveGroupBuy(productId);
        boolean wasIndexed = product.getStatus() == ProductStatus.APPROVED;
        String thumbnailUrl = product.getThumbnailUrl();
        product.delete(adminId, reason);
        if (wasIndexed) {
            outboxRepository.save(ProductSearchEventOutbox.delete(productId));
        }
        if (productImageUploadService.isOurBucketUrl(thumbnailUrl)) {
            eventPublisher.publishEvent(new ProductImageOrphanedEvent(thumbnailUrl));
        }
        List<ProductImage> extraImages = productImageRepository.findByProductId(productId);
        extraImages.stream()
                .map(ProductImage::getImageUrl)
                .filter(productImageUploadService::isOurBucketUrl)
                .forEach(url -> eventPublisher.publishEvent(new ProductImageOrphanedEvent(url)));
        productImageRepository.deleteByProductId(productId);
    }

    // 진행 중인(READY/ONGOING) 공동구매가 있으면 상품 삭제를 막는다
    private void validateNoActiveGroupBuy(Long productId) {
        boolean hasActiveGroupBuy = groupBuyRepository.existsByProductIdAndStatusIn(
                productId, List.of(GroupBuyStatus.READY, GroupBuyStatus.ONGOING));
        if (hasActiveGroupBuy) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_ACTIVE_PRODUCT);
        }
    }

    private Product findProduct(Long productId) {
        return productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }
}
