package com.wellbuying.domain.payment.event;

public enum PaymentEventType {

    PAYMENT_COMPLETED("PaymentCompleted"),
    PAYMENT_FAILED("PaymentFailed");

    private final String code;

    PaymentEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
