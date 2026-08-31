package com.wellbuying.domain.payment.gateway;

import java.util.Optional;

// 회원의 빌링키를 가져오는 경계.
// 빌링키를 어느 테이블에 저장할지가 GroupBuy 도메인과 협의 중이라(00-payment-design.md 참고),
// 저장 위치가 정해질 때까지 이 인터페이스의 구현체만 교체하면 되도록 분리해 둔다
public interface BillingKeyProvider {

    Optional<String> findBillingKey(Long memberId);
}
