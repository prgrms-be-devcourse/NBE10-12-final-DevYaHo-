package com.wellbuying.domain.groupbuy.event;

import com.wellbuying.domain.address.entity.BuyerAddress;
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
        // 발행 시점에 참여 건의 buyer_address_id로 조회한 주소록 텍스트. 결제 도메인이 주문(ORDERS.shipping_address)을
        // 만들 때 쓰며, 값이 없으면 결제가 승인 전에 실패하므로 성사 이벤트에 반드시 실어야 한다
        String shippingAddress,
        LocalDateTime occurredAt
) {

    // buyerAddress는 호출자(GroupBuyEventPublisher)가 part.getBuyerAddressId()로 미리 조회해 전달한다 -
    // 참여 건 자체는 주소록을 참조만 할 뿐 텍스트를 갖고 있지 않기 때문
    public static GroupBuyCompletedEvent of(GroupBuy groupBuy, GroupBuyPart part, BuyerAddress buyerAddress) {
        return new GroupBuyCompletedEvent(
                GroupBuyEventType.GROUP_BUY_COMPLETED.code(),
                groupBuy.getId(),
                groupBuy.getProductId(),
                groupBuy.getProducerId(),
                part.getId(),
                part.getMemberId(),
                part.getQuantity(),
                part.getAppliedPrice(),
                shippingAddressOf(buyerAddress),
                LocalDateTime.now());
    }

    // 주소록에는 주소/상세주소/우편번호가 나뉘어 있지만 수신 측은 한 컬럼에 담으므로 여기서 합친다.
    // 참조가 비어있으면(주소록 미선택 또는 삭제) 합쳐봐야 의미가 없어 null을 돌려준다 - 수신 측이 그걸 보고 승인 전에 걸러낸다
    private static String shippingAddressOf(BuyerAddress buyerAddress) {
        if (buyerAddress == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(buyerAddress.getAddress());
        if (buyerAddress.getAddressDetail() != null && !buyerAddress.getAddressDetail().isBlank()) {
            sb.append(' ').append(buyerAddress.getAddressDetail());
        }
        if (buyerAddress.getZipcode() != null && !buyerAddress.getZipcode().isBlank()) {
            sb.append(" (").append(buyerAddress.getZipcode()).append(')');
        }
        return sb.toString();
    }
}
