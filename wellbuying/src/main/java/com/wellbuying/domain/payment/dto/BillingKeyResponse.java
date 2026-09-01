package com.wellbuying.domain.payment.dto;

import com.wellbuying.domain.payment.entity.BillingKey;

// 등록 여부와 표시용 카드 정보만 담는다 - 빌링키 자체는 어떤 경우에도 응답에 싣지 않는다
public record BillingKeyResponse(boolean registered, String cardCompany, String cardLast4) {

    public static BillingKeyResponse registered(BillingKey billingKey) {
        return new BillingKeyResponse(true, billingKey.getCardCompany(), billingKey.getCardLast4());
    }

    public static BillingKeyResponse notRegistered() {
        return new BillingKeyResponse(false, null, null);
    }
}
