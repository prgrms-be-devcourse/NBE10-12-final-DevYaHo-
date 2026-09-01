package com.wellbuying.domain.payment.repository;

import com.wellbuying.domain.payment.entity.BillingKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingKeyRepository extends JpaRepository<BillingKey, Long> {

    // 자동결제 승인과 등록 여부 조회의 진입점 - 폐기되지 않은 빌링키만 유효하다
    Optional<BillingKey> findByMemberIdAndDeletedAtIsNull(Long memberId);

    boolean existsByMemberIdAndDeletedAtIsNull(Long memberId);

    // 재등록 시 기존 customerKey를 재사용하기 위해 폐기된 행까지 포함해 가장 최근 것을 찾는다.
    // customerKey는 카드가 아니라 회원을 가리키는 값이라, 카드를 바꿔도 같은 값을 유지해야 토스 쪽 고객이 갈라지지 않는다
    Optional<BillingKey> findFirstByMemberIdOrderByIdDesc(Long memberId);
}
