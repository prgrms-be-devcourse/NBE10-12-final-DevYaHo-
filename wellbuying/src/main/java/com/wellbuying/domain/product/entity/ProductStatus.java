package com.wellbuying.domain.product.entity;

public enum ProductStatus {
    PENDING,   // 상품 등록 직후 기본 상태. 관리자 승인 대기중
    APPROVED,  // 관리자 승인 완료, 실제로 판매중인 상태 (기존 ON_SALE)
    REJECTED   // 관리자가 거절한 상태
}
