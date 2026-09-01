package com.wellbuying.domain.payment.gateway;

import com.wellbuying.domain.payment.crypto.BillingKeyEncryptor;
import com.wellbuying.domain.payment.entity.BillingKey;
import com.wellbuying.domain.payment.repository.BillingKeyRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// billing_key 테이블에서 폐기되지 않은 빌링키를 찾아 복호화한다.
// 복호화가 실패하면(마스터 키 교체, 암호문 손상) 빈 Optional을 돌려줘 호출 측이 결제 실패로 처리하게 한다 -
// 여기서 예외를 던지면 승인 전 단계에서 컨슈머가 멈춰 뒤따르는 결제 건까지 밀린다
@Component
public class DbBillingKeyProvider implements BillingKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(DbBillingKeyProvider.class);

    private final BillingKeyRepository billingKeyRepository;
    private final BillingKeyEncryptor billingKeyEncryptor;

    public DbBillingKeyProvider(BillingKeyRepository billingKeyRepository, BillingKeyEncryptor billingKeyEncryptor) {
        this.billingKeyRepository = billingKeyRepository;
        this.billingKeyEncryptor = billingKeyEncryptor;
    }

    @Override
    public Optional<BillingCredential> findBillingKey(Long memberId) {
        Optional<BillingKey> stored = billingKeyRepository.findByMemberIdAndDeletedAtIsNull(memberId);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        BillingKey billingKey = stored.get();
        try {
            return Optional.of(new BillingCredential(
                    billingKeyEncryptor.decrypt(billingKey.getEncryptedBillingKey()),
                    billingKey.getCustomerKey()));
        } catch (RuntimeException e) {
            log.error("빌링키 복호화 실패 - memberId={}, billingKeyId={}", memberId, billingKey.getId(), e);
            return Optional.empty();
        }
    }
}
