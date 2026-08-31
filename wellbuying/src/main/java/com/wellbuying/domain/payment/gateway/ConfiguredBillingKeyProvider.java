package com.wellbuying.domain.payment.gateway;

import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 임시 구현 - 설정에 넣어둔 테스트 빌링키 하나를 모든 회원에게 돌려준다.
// 빌링키 저장 테이블이 확정되면(00-payment-design.md 협의 필요 사항) 회원별 조회 구현으로 교체할 것.
// 값이 비어 있으면 빈 Optional을 돌려주고, 호출 측이 이를 결제 실패로 처리한다
@Component
public class ConfiguredBillingKeyProvider implements BillingKeyProvider {

    private final String testBillingKey;

    public ConfiguredBillingKeyProvider(@Value("${toss.test-billing-key:}") String testBillingKey) {
        this.testBillingKey = testBillingKey;
    }

    @Override
    public Optional<String> findBillingKey(Long memberId) {
        if (testBillingKey == null || testBillingKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(testBillingKey);
    }
}
