package com.wellbuying.domain.order.repository;

import com.wellbuying.domain.order.entity.Order;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// PK는 토스로 보내는 orderId와 같은 값(문자열)이다 - Order 참고
public interface OrderRepository extends JpaRepository<Order, String> {

    // Payment는 승인됐는데 Order 반영만 실패한 건을 재처리할 때, 이미 만들어졌는지 확인
    boolean existsByPaymentId(Long paymentId);

    // 결제/주문 내역 목록 - 정렬은 서비스에서 Pageable로 지정한다
    Page<Order> findByMemberId(Long memberId, Pageable pageable);

    // 주문 상세 - 본인 소유가 아니면 없는 것으로 취급(404)해 주문 존재 여부를 노출하지 않는다
    Optional<Order> findByOrderIdAndMemberId(String orderId, Long memberId);
}
