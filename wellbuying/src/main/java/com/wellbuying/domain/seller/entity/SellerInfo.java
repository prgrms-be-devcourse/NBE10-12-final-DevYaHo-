package com.wellbuying.domain.seller.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import com.wellbuying.domain.seller.crypto.SellerInfoFieldConverter;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "seller_info",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seller_info_member_id",
                columnNames = "member_id"))
public class SellerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "bank_code", nullable = false, length = 10)
    private String bankCode;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    // AES-256-GCM 암호화 저장 (SellerInfoFieldConverter) - 컬럼에는 IV+암호문을 Base64로 인코딩해 저장
    @Convert(converter = SellerInfoFieldConverter.class)
    @Column(name = "account_number", nullable = false, length = 500)
    private String accountNumber;

    @Convert(converter = SellerInfoFieldConverter.class)
    @Column(name = "account_holder", nullable = false, length = 500)
    private String accountHolder;

    @Column(name = "company_name")
    private String companyName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "seller_status")
    private SellerStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "seller_rank")
    private SellerRank rank;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "settlement_cycle", nullable = false, columnDefinition = "settlement_cycle")
    private SettlementCycle settlementCycle;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected SellerInfo() {
    }

    private SellerInfo(Long memberId, String bankCode, String bankName, String accountNumber,
            String accountHolder, String companyName) {
        this.memberId = memberId;
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.companyName = companyName;
        this.status = SellerStatus.PENDING;
        this.rank = SellerRank.SILVER;
        this.settlementCycle = SettlementCycle.MONTHLY;
    }

    // 셀러 신청 생성 - status=PENDING, rank=SILVER, settlementCycle=MONTHLY로 고정 시작 (신청/승인 두 경로 공통 진입점)
    public static SellerInfo apply(Long memberId, String bankCode, String bankName, String accountNumber,
            String accountHolder, String companyName) {
        return new SellerInfo(memberId, bankCode, bankName, accountNumber, accountHolder, companyName);
    }

    // 셀러 승인 - PENDING 상태일 때만 승인 가능, status를 APPROVED로 전환하고 승인 시각을 기록
    public void approve() {
        if (this.status != SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.SELLER_ALREADY_PROCESSED);
        }
        this.status = SellerStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
    }

    // 셀러 거절 - PENDING 상태일 때만 거절 가능, status를 REJECTED로 전환 (approvedAt은 기록하지 않음)
    public void reject() {
        if (this.status != SellerStatus.PENDING) {
            throw new BusinessException(ErrorCode.SELLER_ALREADY_PROCESSED);
        }
        this.status = SellerStatus.REJECTED;
    }

    // 셀러 정지 - APPROVED 상태일 때만 정지 가능, status를 SUSPENDED로 전환
    public void suspend() {
        if (this.status != SellerStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SELLER_NOT_APPROVED);
        }
        this.status = SellerStatus.SUSPENDED;
    }

    // 셀러 정지 복귀 - SUSPENDED 상태일 때만 복귀 가능, status를 다시 APPROVED로 전환
    public void reactivate() {
        if (this.status != SellerStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.SELLER_NOT_SUSPENDED);
        }
        this.status = SellerStatus.APPROVED;
    }

    // 탈퇴 시 삭제 대상인지 판단 - 승인 전(PENDING)/거절(REJECTED) 신청만 삭제, 금융 정보 보유 상태(APPROVED/SUSPENDED)는 보존
    public boolean isDeletableOnWithdraw() {
        return this.status == SellerStatus.PENDING || this.status == SellerStatus.REJECTED;
    }

    // 재신청 - 새 신청 정보로 갱신하고 PENDING으로 되돌림 ("REJECTED여야만 재신청 가능"이라는 검증은 서비스 레이어 책임)
    public void reapply(String bankCode, String bankName, String accountNumber, String accountHolder,
            String companyName) {
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.companyName = companyName;
        this.status = SellerStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public SellerStatus getStatus() {
        return status;
    }

    public String getBankName() {
        return bankName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
