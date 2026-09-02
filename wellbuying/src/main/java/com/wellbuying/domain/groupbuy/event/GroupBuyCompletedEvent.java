package com.wellbuying.domain.groupbuy.event;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import java.time.LocalDateTime;

// 공동구매 성사 이벤트 - 참여자 1명당 1건씩 발행되어, 결제 도메인이 참여자 단위로 결제를 개시할 수 있게 한다
public record GroupBuyCompletedEvent(
        String eventType,
        Long groupBuyId,
        Long productId,
        Long producerId,
        Long partId,
        Long memberId,
        int quantity,
        int appliedPrice,
        // 참여 시점에 얼려둔 배송지. 결제 도메인이 주문(ORDERS.shipping_address)을 만들 때 쓰며,
        // 값이 없으면 결제가 승인 전에 실패하므로 성사 이벤트에 반드시 실어야 한다
        String shippingAddress,
        LocalDateTime occurredAt
) {

    public static GroupBuyCompletedEvent of(GroupBuy groupBuy, GroupBuyPart part) {
        return new GroupBuyCompletedEvent(
                GroupBuyEventType.GROUP_BUY_COMPLETED.code(),
                groupBuy.getId(),
                groupBuy.getProductId(),
                groupBuy.getProducerId(),
                part.getId(),
                part.getMemberId(),
                part.getQuantity(),
                part.getAppliedPrice(),
                shippingAddressOf(part),
                LocalDateTime.now());
    }

    // 참여 행에는 주소/상세주소/우편번호가 나뉘어 있지만 수신 측은 한 컬럼에 담으므로 여기서 합친다.
    // 기본 주소가 없으면 합쳐봐야 의미가 없어 null을 돌려준다 - 수신 측이 그걸 보고 승인 전에 걸러낸다
    private static String shippingAddressOf(GroupBuyPart part) {
        String address = part.getAddress();
        if (address == null || address.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(address);
        if (part.getAddressDetail() != null && !part.getAddressDetail().isBlank()) {
            sb.append(' ').append(part.getAddressDetail());
        }
        if (part.getZipcode() != null && !part.getZipcode().isBlank()) {
            sb.append(" (").append(part.getZipcode()).append(')');
        }
        return sb.toString();
    }
}
