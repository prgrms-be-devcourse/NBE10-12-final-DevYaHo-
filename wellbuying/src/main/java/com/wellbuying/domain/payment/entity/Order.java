package com.wellbuying.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

// 결제 승인이 끝난 뒤에 만들어지는 주문. 이후 배송/구매확정 흐름은 배송 도메인이 status를 전이시킨다
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "group_buy_participant_id", nullable = false)
    private Long groupBuyParticipantId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "order_status")
    private OrderStatus status;

    // 회원의 기본 배송지를 주문 시점에 복사해 둔 값 - 배송지가 나중에 수정돼도 이 주문의 배송지는 유지된다
    @Column(name = "shipping_address", nullable = false)
    private String shippingAddress;

    @Column(name = "total_price", nullable = false)
    private int totalPrice;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Order() {
    }

    private Order(Long paymentId, Long groupBuyParticipantId, Long memberId, String shippingAddress, int totalPrice) {
        this.paymentId = paymentId;
        this.groupBuyParticipantId = groupBuyParticipantId;
        this.memberId = memberId;
        this.shippingAddress = shippingAddress;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.PAID;
    }

    // 결제 승인 직후 생성 - 이 시점엔 이미 결제가 끝났으므로 PENDING이 아니라 PAID로 시작한다
    public static Order paid(Long paymentId, Long groupBuyParticipantId, Long memberId, String shippingAddress,
            int totalPrice) {
        return new Order(paymentId, groupBuyParticipantId, memberId, shippingAddress, totalPrice);
    }

    public Long getId() {
        return id;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getGroupBuyParticipantId() {
        return groupBuyParticipantId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public int getTotalPrice() {
        return totalPrice;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
