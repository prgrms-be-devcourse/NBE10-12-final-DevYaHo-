package com.wellbuying.domain.payment.gateway;

// 토스 빌링키 발급 실패. 메시지에 authKey나 빌링키를 절대 담지 않는다
public class BillingKeyIssueException extends RuntimeException {

    public BillingKeyIssueException(String message) {
        super(message);
    }

    public BillingKeyIssueException(String message, Throwable cause) {
        super(message, cause);
    }
}
