package com.wellbuying.domain.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 정산 확정 집계 1행 = 공동구매 1건.
// SettlementConfirmationWorker(@Scheduled)가 유예기간이 끝난 공동구매의 settlement_item(ACCRUED)을 모아
// 이 행을 만들고 그 item들을 CONFIRMED로 전이한다(Phase 2). 확정 이후 역정산은 없다.
//
// 인덱스/유니크 제약의 실제 소스는 V42__create_settlement.sql이고(ddl-auto=validate라 여기 선언이
// DDL을 생성하지는 않는다), 코드만 보고도 제약을 알 수 있도록 엔티티에도 동일하게 명시해둔다
@Entity
@Table(name = "settlement",
        indexes = @Index(name = "idx_settlement_producer_id", columnList = "producer_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_group_buy_id", columnNames = "group_buy_id"))
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_buy_id", nullable = false, updatable = false)
    private Long groupBuyId;

    @Column(name = "producer_id", nullable = false, updatable = false)
    private Long producerId;

    @Column(name = "item_count", nullable = false, updatable = false)
    private int itemCount;

    @Column(name = "total_sales", nullable = false, updatable = false)
    private long totalSales;

    @Column(name = "platform_fee", nullable = false, updatable = false)
    private long platformFee;

    @Column(nullable = false, updatable = false)
    private long payout;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "settlement_status")
    private SettlementStatus status;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Settlement() {
    }

    private Settlement(Long groupBuyId, Long producerId, int itemCount, long totalSales, long platformFee) {
        this.groupBuyId = groupBuyId;
        this.producerId = producerId;
        this.itemCount = itemCount;
        this.totalSales = totalSales;
        this.platformFee = platformFee;
        this.payout = totalSales - platformFee;
        this.status = SettlementStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    // 유예기간이 끝난 공동구매의 정산을 확정한다. payout(지급 예정액)은 총 매출에서 수수료를 뺀 값으로 파생된다
    public static Settlement confirm(Long groupBuyId, Long producerId, int itemCount, long totalSales,
            long platformFee) {
        return new Settlement(groupBuyId, producerId, itemCount, totalSales, platformFee);
    }

    public Long getId() {
        return id;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getProducerId() {
        return producerId;
    }

    public int getItemCount() {
        return itemCount;
    }

    public long getTotalSales() {
        return totalSales;
    }

    public long getPlatformFee() {
        return platformFee;
    }

    public long getPayout() {
        return payout;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
