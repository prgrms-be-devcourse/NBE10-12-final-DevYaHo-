package com.wellbuying.domain.groupbuy.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPrice;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuyDetailResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyPriceResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyStatusResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.wellbuying.domain.groupbuy.dto.GroupBuyUpdateRequest;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionRequest;
import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionStatus;
import com.wellbuying.domain.groupbuy.dto.GroupBuySuspensionRequestCreateRequest;
import com.wellbuying.domain.groupbuy.dto.GroupBuySuspensionRequestResponse;
import com.wellbuying.domain.groupbuy.event.GroupBuyEventPublisher;
import com.wellbuying.domain.groupbuy.redis.GroupBuyCounterRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPriceRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuySuspensionRequestRepository;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.entity.ProductCategory;
import com.wellbuying.domain.product.repository.ProductCategoryRepository;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.domain.product.service.ProductService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupBuyService {

    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuyPriceRepository groupBuyPriceRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyCounterRepository groupBuyCounterRepository;
    private final GroupBuyEventPublisher groupBuyEventPublisher;
    private final MemberRepository memberRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final GroupBuySuspensionRequestRepository groupBuySuspensionRequestRepository;

    public GroupBuyService(GroupBuyRepository groupBuyRepository, GroupBuyPriceRepository groupBuyPriceRepository,
            GroupBuyPartRepository groupBuyPartRepository, GroupBuyCounterRepository groupBuyCounterRepository,
            GroupBuyEventPublisher groupBuyEventPublisher, MemberRepository memberRepository,
            ProductService productService, ProductRepository productRepository,
            ProductCategoryRepository productCategoryRepository,
            GroupBuySuspensionRequestRepository groupBuySuspensionRequestRepository) {
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuyPriceRepository = groupBuyPriceRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyCounterRepository = groupBuyCounterRepository;
        this.groupBuyEventPublisher = groupBuyEventPublisher;
        this.memberRepository = memberRepository;
        this.productService = productService;
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.groupBuySuspensionRequestRepository = groupBuySuspensionRequestRepository;
    }

    // product의 categoryId로 카테고리명을 조회, 카테고리가 없으면(레거시/삭제된 카테고리 대비) "기타"로 대체
    private String resolveCategoryName(Product product) {
        if (product == null) {
            return "기타";
        }
        return productCategoryRepository.findById(product.getCategoryId())
                .map(ProductCategory::getCategoryName)
                .orElse("기타");
    }

    // 공동구매 생성 - SELLER 역할의 회원만 생산자로 등록 가능, 자기 소유로 등록된 상품이어야 함, 가격 구간과 함께 저장하고 참여용 Redis 카운터를 0으로 초기화
    @Transactional
    public GroupBuyDetailResponse create(Long producerId, GroupBuyCreateRequest request) {
        Member producer = memberRepository.findByIdAndDeletedAtIsNull(producerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (producer.getRole() != Role.SELLER) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FORBIDDEN);
        }
        Product product = productService.getOwnedOrThrow(producerId, request.productId());
        if (!request.startAt().isBefore(request.endAt())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_INVALID_PERIOD);
        }
        if (request.minQuantity() > request.maxQuantity()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_INVALID_QUANTITY);
        }

        GroupBuy groupBuy = groupBuyRepository.save(GroupBuy.create(request.productId(), producerId,
                request.title(), request.startAt(), request.endAt(), request.minQuantity(), request.maxQuantity()));

        List<GroupBuyPrice> priceTiers = request.priceTiers().stream()
                .map(tier -> GroupBuyPrice.of(groupBuy.getId(), tier.tierOrder(), tier.thresholdQuantity(),
                        tier.unitPrice()))
                .toList();
        groupBuyPriceRepository.saveAll(priceTiers);

        Duration ttl = Duration.between(LocalDateTime.now(), request.endAt()).plusDays(1);
        groupBuyCounterRepository.initialize(groupBuy.getId(), ttl);

        return GroupBuyDetailResponse.of(groupBuy, priceTiers, product, resolveCategoryName(product));
    }

    @Transactional(readOnly = true)
    public GroupBuyDetailResponse getDetail(Long groupBuyId) {
        GroupBuy groupBuy = getGroupBuyOrThrow(groupBuyId);
        List<GroupBuyPrice> priceTiers = groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(groupBuyId);
        Product product = productRepository.findById(groupBuy.getProductId()).orElse(null);
        return GroupBuyDetailResponse.of(groupBuy, priceTiers, product, resolveCategoryName(product));
    }

    @Transactional(readOnly = true)
    public GroupBuyStatusResponse getStatus(Long groupBuyId) {
        GroupBuy groupBuy = getGroupBuyOrThrow(groupBuyId);
        long participantCount = groupBuyPartRepository.countByGroupBuyIdAndStatus(groupBuyId,
                GroupBuyPartStatus.CONFIRMED);
        return GroupBuyStatusResponse.of(groupBuy, participantCount);
    }

    @Transactional(readOnly = true)
    public Page<GroupBuySummaryResponse> list(GroupBuyStatus status, Pageable pageable) {
        Page<GroupBuy> page = status != null
                ? groupBuyRepository.findByStatus(status, pageable)
                : groupBuyRepository.findAll(pageable);
        return toSummaryPage(page);
    }

    // 생산자 본인이 개설한 공동구매 목록 조회 - "내 공동구매" 화면(판매정지 요청 대상 선택 등)에서 사용
    @Transactional(readOnly = true)
    public Page<GroupBuySummaryResponse> listMine(Long producerId, GroupBuyStatus status, Pageable pageable) {
        Page<GroupBuy> page = status != null
                ? groupBuyRepository.findByProducerIdAndStatus(producerId, status, pageable)
                : groupBuyRepository.findByProducerId(producerId, pageable);
        return toSummaryPage(page);
    }

    // 상품/카테고리를 배치 조회해 GroupBuySummaryResponse로 조합 - list()/listMine()이 공유
    private Page<GroupBuySummaryResponse> toSummaryPage(Page<GroupBuy> page) {
        List<Long> productIds = page.getContent().stream().map(GroupBuy::getProductId).distinct().toList();
        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Long> categoryIds = productsById.values().stream().map(Product::getCategoryId).distinct().toList();
        Map<Long, String> categoryNamesById = productCategoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(ProductCategory::getId, ProductCategory::getCategoryName));
        return page.map(groupBuy -> {
            Product product = productsById.get(groupBuy.getProductId());
            String categoryName = product != null
                    ? categoryNamesById.getOrDefault(product.getCategoryId(), "기타")
                    : "기타";
            return GroupBuySummaryResponse.of(groupBuy, product, categoryName);
        });
    }

    // 판매정지 요청 - 본인 소유의 ONGOING 공동구매만, 이미 처리 대기 중인 요청이 있으면 중복 요청 불가
    @Transactional
    public void requestSuspension(Long producerId, Long groupBuyId, GroupBuySuspensionRequestCreateRequest request) {
        GroupBuy groupBuy = getGroupBuyOrThrow(groupBuyId);
        validateOwner(groupBuy, producerId);
        if (groupBuy.getStatus() != GroupBuyStatus.ONGOING) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_ONGOING);
        }
        if (groupBuySuspensionRequestRepository.existsByGroupBuyIdAndStatus(groupBuyId,
                GroupBuySuspensionStatus.PENDING)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_ALREADY_REQUESTED);
        }
        groupBuySuspensionRequestRepository.save(
                GroupBuySuspensionRequest.request(groupBuyId, producerId, request.reason()));
    }

    // 관리자의 상태별 판매정지 요청 목록 조회 - 공동구매 제목을 함께 보여주기 위해 배치 조회 후 조합
    @Transactional(readOnly = true)
    public Page<GroupBuySuspensionRequestResponse> listSuspensionRequests(GroupBuySuspensionStatus status,
            Pageable pageable) {
        Page<GroupBuySuspensionRequest> page = groupBuySuspensionRequestRepository.findAllByStatus(status, pageable);
        List<Long> groupBuyIds = page.getContent().stream().map(GroupBuySuspensionRequest::getGroupBuyId).distinct()
                .toList();
        Map<Long, String> titlesById = groupBuyRepository.findAllById(groupBuyIds).stream()
                .collect(Collectors.toMap(GroupBuy::getId, GroupBuy::getTitle));
        return page.map(request -> GroupBuySuspensionRequestResponse.of(request,
                titlesById.getOrDefault(request.getGroupBuyId(), "")));
    }

    // 판매정지 요청 승인 - 요청을 APPROVED로 전환하고 대상 공동구매를 suspended=true로 변경
    @Transactional
    public void approveSuspensionRequest(Long requestId) {
        GroupBuySuspensionRequest request = findPendingSuspensionRequest(requestId);
        request.approve();
        GroupBuy groupBuy = getGroupBuyOrThrow(request.getGroupBuyId());
        groupBuy.suspend();
    }

    // 판매정지 요청 반려 - 요청만 REJECTED로 전환, 공동구매 상태는 변경하지 않음
    @Transactional
    public void rejectSuspensionRequest(Long requestId) {
        GroupBuySuspensionRequest request = findPendingSuspensionRequest(requestId);
        request.reject();
    }

    private GroupBuySuspensionRequest findPendingSuspensionRequest(Long requestId) {
        GroupBuySuspensionRequest request = groupBuySuspensionRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_NOT_FOUND));
        if (request.getStatus() != GroupBuySuspensionStatus.PENDING) {
            throw new BusinessException(ErrorCode.GROUP_BUY_SUSPENSION_ALREADY_PROCESSED);
        }
        return request;
    }

    // 공동구매는 생성 시 가격 구간이 최소 1개 이상 있어야 하고 이후 삭제되지 않으므로,
    // 빈 결과는 곧 공동구매가 존재하지 않는다는 뜻이다 - 존재 여부만 확인하는 별도 쿼리를 두지 않는다
    @Transactional(readOnly = true)
    public List<GroupBuyPriceResponse> getPriceTiers(Long groupBuyId) {
        List<GroupBuyPrice> priceTiers = groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(groupBuyId);
        if (priceTiers.isEmpty()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND);
        }
        return priceTiers.stream()
                .map(GroupBuyPriceResponse::of)
                .toList();
    }

    // 정보 수정 - 시작 전(READY)에만 허용
    @Transactional
    public GroupBuyDetailResponse update(Long producerId, Long groupBuyId, GroupBuyUpdateRequest request) {
        GroupBuy groupBuy = getGroupBuyOrThrow(groupBuyId);
        validateOwner(groupBuy, producerId);
        if (groupBuy.getStatus() != GroupBuyStatus.READY) {
            throw new BusinessException(ErrorCode.GROUP_BUY_UPDATE_NOT_ALLOWED);
        }
        if (request.endAt() != null && !groupBuy.getStartAt().isBefore(request.endAt())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_INVALID_PERIOD);
        }

        groupBuy.updateInfo(request.title(), request.endAt());
        List<GroupBuyPrice> priceTiers = groupBuyPriceRepository.findByGroupBuyIdOrderByTierOrderAsc(groupBuyId);
        Product product = productRepository.findById(groupBuy.getProductId()).orElse(null);
        return GroupBuyDetailResponse.of(groupBuy, priceTiers, product, resolveCategoryName(product));
    }

    // 취소 - 시작 전(READY)에만 허용
    @Transactional
    public void cancel(Long producerId, Long groupBuyId) {
        GroupBuy groupBuy = getGroupBuyOrThrow(groupBuyId);
        validateOwner(groupBuy, producerId);
        if (groupBuy.getStatus() != GroupBuyStatus.READY) {
            throw new BusinessException(ErrorCode.GROUP_BUY_CANCEL_NOT_ALLOWED);
        }

        groupBuy.cancel();
        groupBuyCounterRepository.delete(groupBuyId);
        groupBuyEventPublisher.publishCanceled(groupBuy);
    }

    private GroupBuy getGroupBuyOrThrow(Long groupBuyId) {
        return groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_BUY_NOT_FOUND));
    }

    private void validateOwner(GroupBuy groupBuy, Long producerId) {
        if (!groupBuy.getProducerId().equals(producerId)) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FORBIDDEN);
        }
    }
}
