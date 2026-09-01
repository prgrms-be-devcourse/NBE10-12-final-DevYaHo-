package com.wellbuying.domain.payment.service;

import com.wellbuying.domain.payment.crypto.BillingKeyEncryptor;
import com.wellbuying.domain.payment.dto.BillingKeyAuthRequestResponse;
import com.wellbuying.domain.payment.dto.BillingKeyResponse;
import com.wellbuying.domain.payment.entity.BillingKey;
import com.wellbuying.domain.payment.gateway.BillingKeyIssueException;
import com.wellbuying.domain.payment.gateway.BillingKeyIssueResult;
import com.wellbuying.domain.payment.gateway.TossBillingKeyClient;
import com.wellbuying.domain.payment.repository.BillingKeyRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// 카드 등록 유스케이스. 이 클래스에는 @Transactional을 걸지 않는다 -
// 토스 호출을 트랜잭션 밖에 두기 위해 DB 구간은 전부 BillingKeyTransactionService 호출로만 열고 닫는다
@Service
public class BillingKeyService {

    private static final Logger log = LoggerFactory.getLogger(BillingKeyService.class);

    private final BillingKeyRepository billingKeyRepository;
    private final BillingKeyTransactionService billingKeyTransactionService;
    private final TossBillingKeyClient tossBillingKeyClient;
    private final BillingKeyEncryptor billingKeyEncryptor;

    public BillingKeyService(BillingKeyRepository billingKeyRepository,
            BillingKeyTransactionService billingKeyTransactionService, TossBillingKeyClient tossBillingKeyClient,
            BillingKeyEncryptor billingKeyEncryptor) {
        this.billingKeyRepository = billingKeyRepository;
        this.billingKeyTransactionService = billingKeyTransactionService;
        this.tossBillingKeyClient = tossBillingKeyClient;
        this.billingKeyEncryptor = billingKeyEncryptor;
    }

    // 카드 등록 창에 넘길 customerKey를 내려준다.
    // 이미 발급받은 적이 있으면 폐기된 건이라도 그 값을 재사용한다 - customerKey는 카드가 아니라
    // 회원을 가리키는 값이라, 카드를 바꿀 때마다 새로 만들면 토스 쪽 고객이 갈라진다
    public BillingKeyAuthRequestResponse issueCustomerKey(Long memberId) {
        String customerKey = billingKeyRepository.findFirstByMemberIdOrderByIdDesc(memberId)
                .map(BillingKey::getCustomerKey)
                .orElseGet(() -> UUID.randomUUID().toString());
        return new BillingKeyAuthRequestResponse(customerKey);
    }

    // 프런트가 결제창에서 받은 authKey를 빌링키로 교환해 저장한다.
    // customerKey는 발급 때 쓴 값과 승인 때 보내는 값이 같아야 하므로 빌링키와 함께 저장한다
    public BillingKeyResponse register(Long memberId, String authKey, String customerKey) {
        BillingKeyIssueResult issued;
        try {
            issued = tossBillingKeyClient.issue(authKey, customerKey);
        } catch (BillingKeyIssueException e) {
            // 예외 메시지에 authKey/빌링키가 담기지 않도록 memberId만 남긴다
            log.warn("빌링키 발급 실패 - memberId={}", memberId, e);
            throw new BusinessException(ErrorCode.BILLING_KEY_ISSUE_FAILED);
        }

        BillingKey saved = billingKeyTransactionService.replace(
                memberId,
                customerKey,
                billingKeyEncryptor.encrypt(issued.billingKey()),
                issued.cardCompany(),
                issued.cardLast4());

        return BillingKeyResponse.registered(saved);
    }

    public BillingKeyResponse find(Long memberId) {
        return billingKeyRepository.findByMemberIdAndDeletedAtIsNull(memberId)
                .map(BillingKeyResponse::registered)
                .orElseGet(BillingKeyResponse::notRegistered);
    }

    // 폐기는 로컬 상태만 바꾼다. 토스 측 빌링키 삭제 API 존재 여부는 02-billingkey.md 조사 2번에서
    // 확인한 뒤 이 메서드에 이어 붙인다 (확인 전까지 토스에는 빌링키가 남는다)
    public void discard(Long memberId) {
        if (!billingKeyTransactionService.discard(memberId)) {
            throw new BusinessException(ErrorCode.BILLING_KEY_NOT_FOUND);
        }
    }
}
