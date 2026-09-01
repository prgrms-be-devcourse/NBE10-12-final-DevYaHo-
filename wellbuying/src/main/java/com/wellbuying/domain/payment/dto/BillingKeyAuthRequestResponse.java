package com.wellbuying.domain.payment.dto;

// 카드 등록 창(requestBillingAuth) 호출에 넘길 고객 식별자
public record BillingKeyAuthRequestResponse(String customerKey) {
}
