package com.wellbuying.domain.payment.service;

// PG 승인 후 Order 생성 단계에서 깨진 경우를 커밋 실패와 구분하기 위한 표식.
// 어느 쪽이든 트랜잭션은 롤백되지만, 실패 로그에 원인을 남겨야 수동 처리가 쉬워진다
public class OrderCreationException extends RuntimeException {

    public OrderCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
