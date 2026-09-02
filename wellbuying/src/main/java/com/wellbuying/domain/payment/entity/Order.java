package com.wellbuying.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

// PG 승인을 요청하기 전에 PENDING으로 먼저 만들어 두는 주문.
// 승인 응답을 받기 전에 서버가 죽어도 "결제를 시도했다"는 사실이 구매자에게 보이는 형태로 남고,
// 승인 결과가 나오면 상태만 PAID/PAYMENT_FAILED로 전이한다.
// 이후 배송/구매확정 흐름은 배송 도메인이 status를 이어서 전이시킨다
@Entity
@Table(name = "orders")
public class Order implements Persistable<String> {

    // 토스에 보내는 orderId와 같은 값이다. 토스 대시보드의 주문번호로 이 행을 바로 찾을 수 있고,
    // 재시도할 때도 저장된 값을 그대로 보내므로 멱등키와 요청 본문이 어긋나지 않는다.
    // 토스 규격은 영문/숫자/-/_ 로 이루어진 6~64자 - 'gb-' + UUID = 39자로 항상 충족한다
    @Id
    @Column(name = "order_id", length = 64, updatable = false)
    private String orderId;

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

    // 식별자를 우리가 직접 채우므로 Spring Data는 이 엔티티를 "이미 존재하는 행"으로 오해해
    // save() 때 persist 대신 merge를 돌린다(= INSERT 전에 불필요한 SELECT 1회).
    // 신규 여부를 명시해 결제 경로에서 그 조회를 없앤다
    @Transient
    private boolean isNew = true;

    protected Order() {
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public String getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    private Order(Long paymentId, Long groupBuyParticipantId, Long memberId, String shippingAddress, int totalPrice) {
        this.orderId = "gb-" + UUID.randomUUID();
        this.paymentId = paymentId;
        this.groupBuyParticipantId = groupBuyParticipantId;
        this.memberId = memberId;
        this.shippingAddress = shippingAddress;
        this.totalPrice = totalPrice;
        this.status = OrderStatus.PENDING;
    }

    // PG 승인을 요청하기 전에 결제 건과 같은 트랜잭션에서 만든다 (결제 대기 상태)
    public static Order pending(Long paymentId, Long groupBuyParticipantId, Long memberId, String shippingAddress,
            int totalPrice) {
        return new Order(paymentId, groupBuyParticipantId, memberId, shippingAddress, totalPrice);
    }

    public void markPaid() {
        this.status = OrderStatus.PAID;
    }

    public void markPaymentFailed() {
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public String getOrderId() {
        return orderId;
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
