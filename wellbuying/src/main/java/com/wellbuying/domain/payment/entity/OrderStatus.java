package com.wellbuying.domain.payment.entity;

// ERD의 한글 상태값을 코드베이스 ENUM 컨벤션(영문)에 맞춘 것 - 매핑은 V14__payment_domain.sql 주석 참고
public enum OrderStatus {

    PENDING,
    PAID,
    PAYMENT_FAILED,
    PREPARING,
    SHIPPING,
    DELIVERED,
    CONFIRMED,
    CANCELED
}
