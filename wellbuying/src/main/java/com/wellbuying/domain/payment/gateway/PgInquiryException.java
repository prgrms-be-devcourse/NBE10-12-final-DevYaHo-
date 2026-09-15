package com.wellbuying.domain.payment.gateway;

// 결제조회(inquire) 호출 자체가 실패한 경우(네트워크 오류 등).
// "결제가 없다"는 정상 조회 결과(PgInquiryResult의 NOT_FOUND_PAYMENT)와는 다르다 - 그건 예외가 아니다
public class PgInquiryException extends RuntimeException {

    public PgInquiryException(String message, Throwable cause) {
        super(message, cause);
    }
}
