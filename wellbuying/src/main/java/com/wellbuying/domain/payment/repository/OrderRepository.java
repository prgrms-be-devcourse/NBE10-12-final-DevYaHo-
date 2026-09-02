package com.wellbuying.domain.payment.repository;

import com.wellbuying.domain.payment.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

// PK는 토스로 보내는 orderId와 같은 값(문자열)이다 - Order 참고
public interface OrderRepository extends JpaRepository<Order, String> {

    // Payment는 승인됐는데 Order 반영만 실패한 건을 재처리할 때, 이미 만들어졌는지 확인
    boolean existsByPaymentId(Long paymentId);
}
