package com.wellbuying.domain.payment.service;

import com.wellbuying.domain.payment.entity.BillingKey;
import com.wellbuying.domain.payment.repository.BillingKeyRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 빌링키의 DB 트랜잭션 구간만 담당한다. 토스 호출은 여기 들어오지 않는다 -
// PaymentTransactionService와 같은 이유로, 외부 응답을 기다리는 동안 DB 커넥션을 붙잡지 않기 위해서다
@Service
public class BillingKeyTransactionService {

    private final BillingKeyRepository billingKeyRepository;

    public BillingKeyTransactionService(BillingKeyRepository billingKeyRepository) {
        this.billingKeyRepository = billingKeyRepository;
    }

    // 기존 빌링키를 폐기하고 새 빌링키를 넣는 것을 한 트랜잭션으로 묶는다.
    // 나누면 "예전 카드는 지워졌는데 새 카드는 없는" 상태가 정상 흐름에서도 생긴다.
    // 폐기가 같은 트랜잭션 안에서 먼저 반영돼야 uk_billing_key_member_id_active 제약에 걸리지 않는다
    @Transactional
    public BillingKey replace(Long memberId, String customerKey, String encryptedBillingKey, String cardCompany,
            String cardLast4) {
        billingKeyRepository.findByMemberIdAndDeletedAtIsNull(memberId)
                .ifPresent(existing -> existing.discard(LocalDateTime.now()));
        billingKeyRepository.flush();

        return billingKeyRepository.save(
                BillingKey.issued(memberId, customerKey, encryptedBillingKey, cardCompany, cardLast4));
    }

    @Transactional
    public boolean discard(Long memberId) {
        Optional<BillingKey> active = billingKeyRepository.findByMemberIdAndDeletedAtIsNull(memberId);
        active.ifPresent(billingKey -> billingKey.discard(LocalDateTime.now()));
        return active.isPresent();
    }
}
