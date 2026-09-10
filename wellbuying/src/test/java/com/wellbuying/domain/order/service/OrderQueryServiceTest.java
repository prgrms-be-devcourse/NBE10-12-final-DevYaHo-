package com.wellbuying.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPartStatus;
import com.wellbuying.domain.groupbuy.repository.GroupBuyPartRepository;
import com.wellbuying.domain.groupbuy.repository.GroupBuyRepository;
import com.wellbuying.domain.order.dto.OrderDetailResponse;
import com.wellbuying.domain.order.dto.OrderSummaryResponse;
import com.wellbuying.domain.order.entity.Order;
import com.wellbuying.domain.order.entity.OrderStatus;
import com.wellbuying.domain.order.repository.OrderRepository;
import com.wellbuying.domain.payment.entity.Payment;
import com.wellbuying.domain.payment.entity.PaymentStatus;
import com.wellbuying.domain.payment.repository.PaymentRepository;
import com.wellbuying.domain.product.entity.Product;
import com.wellbuying.domain.product.repository.ProductRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderQueryServiceTest {

    private static final Long MEMBER_ID = 100L;
    private static final Long PART_ID = 7L;
    private static final Long GROUP_BUY_ID = 3L;
    private static final Long PRODUCT_ID = 11L;
    private static final Long PAYMENT_ID = 55L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private GroupBuyPartRepository groupBuyPartRepository;
    @Mock
    private GroupBuyRepository groupBuyRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private PaymentRepository paymentRepository;

    private OrderQueryService service() {
        return new OrderQueryService(orderRepository, groupBuyPartRepository, groupBuyRepository, productRepository,
                paymentRepository);
    }

    private Order order() {
        Order order = Order.pending(PAYMENT_ID, PART_ID, MEMBER_ID, "서울시 강남구 123", 30_000);
        order.markPaid();
        return order;
    }

    private GroupBuyPart part() {
        GroupBuyPart part = GroupBuyPart.confirm(GROUP_BUY_ID, MEMBER_ID, 3);
        ReflectionTestUtils.setField(part, "id", PART_ID);
        part.applyFinalPrice(10_000);
        return part;
    }

    private GroupBuy groupBuy() {
        GroupBuy groupBuy = GroupBuy.create(PRODUCT_ID, 200L, "제철 사과 공동구매",
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1), 1, 100);
        ReflectionTestUtils.setField(groupBuy, "id", GROUP_BUY_ID);
        return groupBuy;
    }

    private Product product() {
        Product product = Product.register(1L, 2L, "청송 사과 5kg", "당도 높은 사과", 12_000, "https://cdn/apple.jpg");
        ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
        return product;
    }

    private Payment approvedPayment() {
        Payment payment = Payment.ready(PART_ID, MEMBER_ID, 30_000, "TOSS", "GroupBuyCompleted:" + PART_ID);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        payment.approve("toss-tx-abc", LocalDateTime.now().minusDays(1));
        return payment;
    }

    @Test
    void getMyOrders는_주문에_상품_수량_공구제목을_조합해_돌려준다() {
        Page<Order> page = new PageImpl<>(List.of(order()));
        when(orderRepository.findByMemberId(eq(MEMBER_ID), any())).thenReturn(page);
        when(groupBuyPartRepository.findAllById(any())).thenReturn(List.of(part()));
        when(groupBuyRepository.findAllById(any())).thenReturn(List.of(groupBuy()));
        when(productRepository.findAllById(any())).thenReturn(List.of(product()));

        Page<OrderSummaryResponse> result = service().getMyOrders(MEMBER_ID, PageRequest.of(0, 10));

        assertThat(result).hasSize(1);
        OrderSummaryResponse row = result.getContent().get(0);
        assertThat(row.groupBuyId()).isEqualTo(GROUP_BUY_ID);
        assertThat(row.groupBuyTitle()).isEqualTo("제철 사과 공동구매");
        assertThat(row.productName()).isEqualTo("청송 사과 5kg");
        assertThat(row.thumbnailUrl()).isEqualTo("https://cdn/apple.jpg");
        assertThat(row.quantity()).isEqualTo(3);
        assertThat(row.totalPrice()).isEqualTo(30_000);
        assertThat(row.status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void getMyOrders는_교차도메인_행이_없어도_빈값으로_안전하게_처리한다() {
        when(orderRepository.findByMemberId(eq(MEMBER_ID), any())).thenReturn(new PageImpl<>(List.of(order())));
        when(groupBuyPartRepository.findAllById(any())).thenReturn(List.of());
        when(groupBuyRepository.findAllById(any())).thenReturn(List.of());
        when(productRepository.findAllById(any())).thenReturn(List.of());

        Page<OrderSummaryResponse> result = service().getMyOrders(MEMBER_ID, PageRequest.of(0, 10));

        OrderSummaryResponse row = result.getContent().get(0);
        assertThat(row.groupBuyId()).isNull();
        assertThat(row.groupBuyTitle()).isEmpty();
        assertThat(row.productName()).isEmpty();
        assertThat(row.quantity()).isZero();
        assertThat(row.totalPrice()).isEqualTo(30_000);
    }

    @Test
    void getMyOrderDetail은_주문_결제_상품_정보를_합쳐_돌려준다() {
        when(orderRepository.findByOrderIdAndMemberId(any(), eq(MEMBER_ID))).thenReturn(Optional.of(order()));
        when(groupBuyPartRepository.findById(PART_ID)).thenReturn(Optional.of(part()));
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy()));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(approvedPayment()));

        OrderDetailResponse detail = service().getMyOrderDetail(MEMBER_ID, "gb-any");

        assertThat(detail.productName()).isEqualTo("청송 사과 5kg");
        assertThat(detail.quantity()).isEqualTo(3);
        assertThat(detail.unitPrice()).isEqualTo(10_000);
        assertThat(detail.totalPrice()).isEqualTo(30_000);
        assertThat(detail.shippingAddress()).isEqualTo("서울시 강남구 123");
        assertThat(detail.pgProvider()).isEqualTo("TOSS");
        assertThat(detail.pgTransactionId()).isEqualTo("toss-tx-abc");
        assertThat(detail.paymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(detail.approvedAt()).isNotNull();
    }

    @Test
    void getMyOrderDetail은_본인_소유가_아니면_ORDER_NOT_FOUND() {
        when(orderRepository.findByOrderIdAndMemberId(any(), eq(MEMBER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMyOrderDetail(MEMBER_ID, "gb-someone-else"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void getMyOrderIdByGroupBuy는_참여자와_주문을_연쇄조회해_orderId를_돌려준다() {
        Order order = order();
        when(groupBuyPartRepository.findByGroupBuyIdAndMemberIdAndStatus(GROUP_BUY_ID, MEMBER_ID,
                GroupBuyPartStatus.CONFIRMED)).thenReturn(Optional.of(part()));
        when(orderRepository.findFirstByGroupBuyParticipantIdAndMemberIdOrderByCreatedAtDesc(PART_ID, MEMBER_ID))
                .thenReturn(Optional.of(order));

        String orderId = service().getMyOrderIdByGroupBuy(MEMBER_ID, GROUP_BUY_ID);

        assertThat(orderId).isEqualTo(order.getOrderId());
    }

    @Test
    void getMyOrderIdByGroupBuy는_참여내역이_없으면_ORDER_NOT_FOUND() {
        when(groupBuyPartRepository.findByGroupBuyIdAndMemberIdAndStatus(GROUP_BUY_ID, MEMBER_ID,
                GroupBuyPartStatus.CONFIRMED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMyOrderIdByGroupBuy(MEMBER_ID, GROUP_BUY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void getMyOrderIdByGroupBuy는_참여는_있어도_주문이_없으면_ORDER_NOT_FOUND() {
        when(groupBuyPartRepository.findByGroupBuyIdAndMemberIdAndStatus(GROUP_BUY_ID, MEMBER_ID,
                GroupBuyPartStatus.CONFIRMED)).thenReturn(Optional.of(part()));
        when(orderRepository.findFirstByGroupBuyParticipantIdAndMemberIdOrderByCreatedAtDesc(PART_ID, MEMBER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getMyOrderIdByGroupBuy(MEMBER_ID, GROUP_BUY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }
}
