package com.wellbuying.domain.payment.gateway;

// 자동결제 승인에 필요한 한 쌍.
// 발급 때 쓴 customerKey와 승인 때 보내는 customerKey가 다르면 토스가 승인을 거부하므로,
// 빌링키만 따로 넘기지 않고 항상 둘을 함께 들고 다닌다
public record BillingCredential(String billingKey, String customerKey) {
}
