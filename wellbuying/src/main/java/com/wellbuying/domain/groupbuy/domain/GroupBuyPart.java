package com.wellbuying.domain.groupbuy.domain;

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
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "group_buy_part")
public class GroupBuyPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int quantity;

    // 공동구매가 성사되기 전까지는 null - 참여 시점의 구간가는 어차피 성사 시 최종가로 소급 확정되거나
    // (실패 시) 아예 쓰이지 않으므로, 참여 시점에는 계산도 저장도 하지 않고 성사되는 순간 단 한 번만 채운다
    @Column(name = "applied_price")
    private Integer appliedPrice;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "group_buy_part_status")
    private GroupBuyPartStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected GroupBuyPart() {
    }

    private GroupBuyPart(Long groupBuyId, Long memberId, int quantity, GroupBuyPartStatus status) {
        this.groupBuyId = groupBuyId;
        this.memberId = memberId;
        this.quantity = quantity;
        this.status = status;
    }

    // 참여 신청 생성 - Redis 원자적 카운터 증가에 성공한 직후 CONFIRMED 상태로 즉시 생성 (결제 단계가 아직 없어 PENDING을 거치지 않음)
    // 가격은 아직 미정(appliedPrice=null) - 공동구매가 성사될 때 applyFinalPrice()로 한 번만 확정된다
    public static GroupBuyPart confirm(Long groupBuyId, Long memberId, int quantity) {
        return new GroupBuyPart(groupBuyId, memberId, quantity, GroupBuyPartStatus.CONFIRMED);
    }

    // 참여 취소
    public void cancel() {
        this.status = GroupBuyPartStatus.CANCELED;
    }

    // 공동구매 성사 시 최종 확정 수량 기준 단가를 채운다 - 참여 시점과 무관하게 모든 참여자가 같은 최종가를 낸다
    public void applyFinalPrice(int finalPrice) {
        this.appliedPrice = finalPrice;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public int getQuantity() {
        return quantity;
    }

    // 공동구매가 아직 성사되지 않았다면 null
    public Integer getAppliedPrice() {
        return appliedPrice;
    }

    public GroupBuyPartStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
