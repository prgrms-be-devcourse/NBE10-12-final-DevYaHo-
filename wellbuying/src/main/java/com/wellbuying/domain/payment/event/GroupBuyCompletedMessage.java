package com.wellbuying.domain.payment.event;

import java.time.LocalDateTime;

// groupbuy-events 토픽 수신용 DTO.
// 발행 측(GroupBuyCompletedEvent) 클래스를 직접 참조하지 않고 결제 도메인이 필요한 필드만 따로 정의한다 -
// 도메인 간 결합을 줄이고, 발행 측에 필드가 추가돼도 이쪽이 깨지지 않게 하기 위함
public record GroupBuyCompletedMessage(
        String eventType,
        Long groupBuyId,
        Long productId,
        Long producerId,
        Long partId,
        Long memberId,
        int quantity,
        Integer appliedPrice,
        // 배송지는 GroupBuy가 이벤트에 담아 보내주기로 한 값 - 발행 측에 아직 필드가 없어 당분간 null로 들어온다
        // (null이면 승인을 시도하지 않고 실패 처리한다. 00-payment-design.md 협의 필요 사항 참고)
        String shippingAddress,
        LocalDateTime occurredAt
) {

    public static final String TYPE = "GroupBuyCompleted";

    // 발행 측 이벤트에 고유 id가 아직 없어 조합해서 만든다
    // 같은 참여 건은 몇 번을 재수신해도 같은 값이 나와야 멱등성 체크가 성립한다
    public String eventId() {
        return eventType + ":" + partId;
    }

    public int totalAmount() {
        return appliedPrice * quantity;
    }

    public boolean hasShippingAddress() {
        return shippingAddress != null && !shippingAddress.isBlank();
    }
}
