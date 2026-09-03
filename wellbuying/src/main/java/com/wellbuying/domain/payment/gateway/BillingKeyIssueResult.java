package com.wellbuying.domain.payment.gateway;

// 토스 빌링키 발급 응답에서 우리가 쓰는 값만 추린 것.
// cardCompany/cardLast4는 마이페이지 표시 전용이라 없어도 결제에는 지장이 없다
public record BillingKeyIssueResult(String billingKey, String cardCompany, String cardLast4) {
}
