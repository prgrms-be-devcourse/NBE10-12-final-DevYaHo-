package com.wellbuying.domain.groupbuy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_buy_price")
public class GroupBuyPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "tier_order", nullable = false)
    private int tierOrder;

    @Column(name = "threshold_quantity", nullable = false)
    private int thresholdQuantity;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    protected GroupBuyPrice() {
    }

    private GroupBuyPrice(Long groupBuyId, int tierOrder, int thresholdQuantity, int unitPrice) {
        this.groupBuyId = groupBuyId;
        this.tierOrder = tierOrder;
        this.thresholdQuantity = thresholdQuantity;
        this.unitPrice = unitPrice;
    }

    // 가격 구간 생성 - 누적 참여 수량이 thresholdQuantity 이상이 되면 unitPrice가 적용된다
    public static GroupBuyPrice of(Long groupBuyId, int tierOrder, int thresholdQuantity, int unitPrice) {
        return new GroupBuyPrice(groupBuyId, tierOrder, thresholdQuantity, unitPrice);
    }

    public Long getId() {
        return id;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public int getTierOrder() {
        return tierOrder;
    }

    public int getThresholdQuantity() {
        return thresholdQuantity;
    }

    public int getUnitPrice() {
        return unitPrice;
    }
}
