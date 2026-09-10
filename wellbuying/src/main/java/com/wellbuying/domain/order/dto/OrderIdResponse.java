package com.wellbuying.domain.order.dto;

// 알림 클릭 시 groupBuyId로 해당 주문을 찾기 위한 응답 - 프론트가 이 orderId로 결제 상세 모달을 연다
public record OrderIdResponse(String orderId) {
}
