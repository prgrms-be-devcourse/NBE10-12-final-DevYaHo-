package com.wellbuying.domain.order.service;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.order.dto.OrderDetailResponse;
import com.wellbuying.domain.order.dto.OrderSummaryResponse;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.repository.OrderRepository;
import com.wellbuying.domain.payment.entity.Payment;
import com.wellbuying.domain.payment.repository.PaymentRepository;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 구매자가 자기 결제/주문 내역을 조회한다. 주문 자체는 order 도메인 소유지만, 표시에 필요한
// 상품명·수량·공구 제목은 groupbuy/product 도메인 행을, 결제 수단 정보는 payment 도메인 행을
// 조회 시점에 배치로 읽어 조합한다 (Order에 스냅샷으로 복사하지 않는다).
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final GroupBuyPartRepository groupBuyPartRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;

    public OrderQueryService(OrderRepository orderRepository, GroupBuyPartRepository groupBuyPartRepository,
            GroupBuyRepository groupBuyRepository, ProductRepository productRepository,
            PaymentRepository paymentRepository) {
        this.orderRepository = orderRepository;
        this.groupBuyPartRepository = groupBuyPartRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
    }

    // 내 결제/주문 내역 목록 - 최신순. 결제를 시도한 모든 상태(PENDING/PAID/PAYMENT_FAILED + 배송 상태)를 그대로 노출한다
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getMyOrders(Long memberId, Pageable pageable) {
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "orderId"));
        Page<Order> orders = orderRepository.findByMemberId(memberId, sorted);

        List<Long> partIds = orders.getContent().stream().map(Order::getGroupBuyParticipantId).distinct().toList();
        Map<Long, GroupBuyPart> partsById = groupBuyPartRepository.findAllById(partIds).stream()
                .collect(Collectors.toMap(GroupBuyPart::getId, Function.identity()));

        List<Long> groupBuyIds = partsById.values().stream().map(GroupBuyPart::getGroupBuyId).distinct().toList();
        Map<Long, GroupBuy> groupBuysById = groupBuyRepository.findAllById(groupBuyIds).stream()
                .collect(Collectors.toMap(GroupBuy::getId, Function.identity()));

        List<Long> productIds = groupBuysById.values().stream().map(GroupBuy::getProductId).distinct().toList();
        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return orders.map(order -> {
            GroupBuyPart part = partsById.get(order.getGroupBuyParticipantId());
            GroupBuy groupBuy = part != null ? groupBuysById.get(part.getGroupBuyId()) : null;
            Product product = groupBuy != null ? productsById.get(groupBuy.getProductId()) : null;
            return OrderSummaryResponse.of(order, part, groupBuy, product);
        });
    }

    // 주문 1건의 결제 정보 상세 - 본인 소유가 아니면 404 (주문 존재 여부를 노출하지 않는다)
    @Transactional(readOnly = true)
    public OrderDetailResponse getMyOrderDetail(Long memberId, String orderId) {
        Order order = orderRepository.findByOrderIdAndMemberId(orderId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        GroupBuyPart part = groupBuyPartRepository.findById(order.getGroupBuyParticipantId()).orElse(null);
        GroupBuy groupBuy = part != null ? groupBuyRepository.findById(part.getGroupBuyId()).orElse(null) : null;
        Product product = groupBuy != null ? productRepository.findById(groupBuy.getProductId()).orElse(null) : null;
        Payment payment = paymentRepository.findById(order.getPaymentId()).orElse(null);

        return OrderDetailResponse.of(order, part, groupBuy, product, payment);
    }
}
