package com.wellbuying.domain.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

// 회원이 카드 인증으로 발급받은 토스 빌링키. 성사 시 자동결제는 이 값으로만 승인할 수 있다.
// 빌링키 자체는 절대 평문으로 들고 다니지 않는다 - 이 엔티티가 보관하는 것도 암호문이며,
// 복호화는 BillingKeyEncryptor를 거치는 경로에서만 일어난다 (05-billingkey-issue.md)
@Entity
@Table(name = "billing_key")
public class BillingKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    // 토스 고객 식별자. 발급 때 쓴 값과 승인 때 보내는 값이 달라지면 승인이 거부되므로 빌링키와 한 행에 둔다
    @Column(name = "customer_key", nullable = false, updatable = false)
    private String customerKey;

    @Column(name = "encrypted_billing_key", nullable = false)
    private String encryptedBillingKey;

    // 표시 전용 - 어느 카드가 등록돼 있는지 사용자에게 보여주기 위한 값이라 없어도 결제에는 지장이 없다
    @Column(name = "card_company")
    private String cardCompany;

    @Column(name = "card_last4")
    private String cardLast4;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected BillingKey() {
    }

    private BillingKey(Long memberId, String customerKey, String encryptedBillingKey, String cardCompany,
            String cardLast4) {
        this.memberId = memberId;
        this.customerKey = customerKey;
        this.encryptedBillingKey = encryptedBillingKey;
        this.cardCompany = cardCompany;
        this.cardLast4 = cardLast4;
    }

    public static BillingKey issued(Long memberId, String customerKey, String encryptedBillingKey, String cardCompany,
            String cardLast4) {
        return new BillingKey(memberId, customerKey, encryptedBillingKey, cardCompany, cardLast4);
    }

    // 카드 교체나 탈퇴로 더 이상 쓰지 않는 빌링키를 폐기한다.
    // 이 시점 이후 uk_billing_key_member_id_active 제약에서 빠지므로 같은 회원의 새 빌링키를 넣을 수 있다
    public void discard(LocalDateTime discardedAt) {
        this.deletedAt = discardedAt;
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getCustomerKey() {
        return customerKey;
    }

    public String getEncryptedBillingKey() {
        return encryptedBillingKey;
    }

    public String getCardCompany() {
        return cardCompany;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
