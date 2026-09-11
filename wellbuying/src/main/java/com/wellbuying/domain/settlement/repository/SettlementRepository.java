package com.wellbuying.domain.settlement.repository;

import com.wellbuying.domain.settlement.entity.Settlement;
import com.wellbuying.domain.settlement.entity.SettlementStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    // 배치 재실행/동시 실행 시 같은 공동구매를 두 번 확정하지 않기 위한 확인용 (UNIQUE 제약과 이중 방어)
    boolean existsByGroupBuyId(Long groupBuyId);

    // 관리자 정산 내역 조회 - 상태 필터 (미지정 시 findAll)
    Page<Settlement> findByStatus(SettlementStatus status, Pageable pageable);

    // 판매자 월별 정산 내역(완료) - group_buy.finalizedAt이 속한 달로 귀속시킨다(settlement.confirmedAt이
    // 아니라). 확정 여부와 무관하게 "이 공동구매가 언제 성사됐는지"로 고정해, 나중에 확정돼도 다른 달로
    // 옮겨가지 않게 한다 (SettlementQueryService 클래스 주석 참고). GroupBuy와 연관관계가 없어 콤마 조인
    // JPQL로 작성 - SettlementItemRepository.findGroupBuyIdsReadyToConfirm과 같은 방식
    @Query(value = """
            SELECT s FROM Settlement s, GroupBuy gb
            WHERE s.groupBuyId = gb.id AND s.producerId = :producerId
              AND gb.finalizedAt >= :from AND gb.finalizedAt < :to
            """,
            countQuery = """
            SELECT COUNT(s) FROM Settlement s, GroupBuy gb
            WHERE s.groupBuyId = gb.id AND s.producerId = :producerId
              AND gb.finalizedAt >= :from AND gb.finalizedAt < :to
            """)
    Page<Settlement> findByProducerIdAndGroupBuyFinalizedAtRange(@Param("producerId") Long producerId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
