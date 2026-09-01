package com.wellbuying.domain.payment.gateway;

import java.util.Optional;

// 회원의 빌링키를 가져오는 경계.
// 성사 이벤트에는 memberId만 실려 오고 빌링키는 결제 도메인이 자기 저장소에서 찾는다
// (02-billingkey.md 방안 A). 저장 방식이 바뀌어도 PaymentProcessor는 이 인터페이스만 본다
public interface BillingKeyProvider {

    Optional<BillingCredential> findBillingKey(Long memberId);
}
