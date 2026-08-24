package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.domain.GroupBuy;
import com.wellbuying.domain.groupbuy.domain.GroupBuyStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuyRepository extends JpaRepository<GroupBuy, Long> {

    // 목록/검색 - 상태별 필터링
    Page<GroupBuy> findByStatus(GroupBuyStatus status, Pageable pageable);

    // 시작 시각이 지난 READY 공동구매 조회 - GroupBuyLifecycleScheduler가 ONGOING으로 전환할 대상
    // limit으로 한 번의 스케줄러 실행에서 처리할 최대 건수를 제한해 대량 적체 시에도 메모리 사용량을 예측 가능하게 유지한다
    // (처리되지 못한 나머지는 상태가 그대로 READY라 다음 실행에서 자연스럽게 이어서 처리된다)
    List<GroupBuy> findByStatusAndStartAtLessThanEqual(GroupBuyStatus status, LocalDateTime now, Limit limit);

    // 마감 시각이 지난 ONGOING 공동구매 조회 - GroupBuyLifecycleScheduler가 SUCCESS/FAILED로 확정할 대상 (limit 설명은 위와 동일)
    List<GroupBuy> findByStatusAndEndAtLessThanEqual(GroupBuyStatus status, LocalDateTime now, Limit limit);

    // 참여 시 누적 수량을 원자적으로 증가시킨다 - "읽은 값 + delta를 자바에서 계산해 덮어쓰는" 방식이 아니라
    // DB가 직접 current_quantity = current_quantity + :quantity 를 한 문장으로 처리하므로, 동시에 여러 참여가
    // 몰려도 갱신이 유실(lost update)되지 않는다. clearAutomatically로 실행 후 영속성 컨텍스트를 비우므로
    // 호출 측은 최신 값이 필요하면 반드시 다시 조회해야 한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GroupBuy g SET g.currentQuantity = g.currentQuantity + :quantity WHERE g.id = :id")
    void increaseQuantity(@Param("id") Long id, @Param("quantity") int quantity);

    // 참여 취소 시 누적 수량을 원자적으로 감소시킨다 (설명은 increaseQuantity와 동일). 0 미만으로는 내려가지 않는다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GroupBuy g SET g.currentQuantity = GREATEST(g.currentQuantity - :quantity, 0) WHERE g.id = :id")
    void decreaseQuantity(@Param("id") Long id, @Param("quantity") int quantity);
}
