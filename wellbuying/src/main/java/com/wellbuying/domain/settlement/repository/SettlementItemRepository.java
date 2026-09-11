package com.wellbuying.domain.settlement.repository;

import com.wellbuying.domain.settlement.entity.SettlementItem;
import com.wellbuying.domain.settlement.entity.SettlementItemStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    // Kafka 재수신으로 같은 결제 완료 이벤트가 다시 소비돼도 중복 적립하지 않기 위한 사전 확인용
    boolean existsByGroupBuyParticipantId(Long groupBuyParticipantId);

    // 이미 확정된 공동구매에 뒤늦게 결제 완료가 도착하는 경우를 걸러내기 위한 확인용
    boolean existsByGroupBuyIdAndStatus(Long groupBuyId, SettlementItemStatus status);

    // 정산 확정 배치 대상: 아직 적립 상태(ACCRUED) item이 남아 있고, 그 공동구매의 유예기간이 끝난 group_buy_id.
    // GroupBuy 엔티티를 함께 조회해 finalized_at 기준으로 거른다 (settlement -> groupbuy 방향 의존은
    // notification 도메인과 동일하게 허용). limit으로 한 번의 배치에서 처리할 최대 건수를 제한한다
    // (처리 못한 나머지는 다음 실행에서 자연스럽게 이어진다 - GroupBuyFinalizationWorker와 같은 방식)
    @Query("""
            SELECT DISTINCT si.groupBuyId
            FROM SettlementItem si, GroupBuy gb
            WHERE si.groupBuyId = gb.id
              AND si.status = :status
              AND gb.finalizedAt IS NOT NULL
              AND gb.finalizedAt < :threshold
              AND NOT EXISTS (SELECT 1 FROM Settlement s WHERE s.groupBuyId = gb.id)
            ORDER BY si.groupBuyId
            """)
    List<Long> findGroupBuyIdsReadyToConfirm(@Param("status") SettlementItemStatus status,
            @Param("threshold") LocalDateTime threshold, Limit limit);

    long countByGroupBuyIdAndStatus(Long groupBuyId, SettlementItemStatus status);

    // 정산 상세 - "결제 완료 인원" 수. ACCRUED/CONFIRMED 상태 무관하게 행이 존재한다는 것 자체가
    // 결제가 끝났다는 뜻이라 상태로 거르지 않는다 (05-monthly-settlement-list.md의 progress 상세 참고)
    long countByGroupBuyId(Long groupBuyId);

    // 정산 상세 - 결제한 참여자 명단(COMPLETED 상세). 결제 순서대로 보여준다
    List<SettlementItem> findByGroupBuyIdOrderByPaidAtAsc(Long groupBuyId);

    // COALESCE: 대상 행이 없을 때 SUM이 null을 반환하므로 0으로 방어 (ProductSearchEventOutboxRepository와 같은 방식)
    @Query("""
            SELECT COALESCE(SUM(si.amount), 0L)
            FROM SettlementItem si
            WHERE si.groupBuyId = :groupBuyId AND si.status = :status
            """)
    long sumAmountByGroupBuyIdAndStatus(@Param("groupBuyId") Long groupBuyId,
            @Param("status") SettlementItemStatus status);

    // 확정 배치가 group_buy 단위로 ACCRUED -> CONFIRMED 일괄 전이. 같은 트랜잭션에서 곧바로 합계를 다시
    // 읽으므로 flush/clear를 켠다 (NotificationRepository.markAllAsRead와 같은 설정)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE SettlementItem si SET si.status = :to
            WHERE si.groupBuyId = :groupBuyId AND si.status = :from
            """)
    int updateStatusByGroupBuyId(@Param("groupBuyId") Long groupBuyId, @Param("from") SettlementItemStatus from,
            @Param("to") SettlementItemStatus to);

    // 매출 추이 그래프 - 이 판매자의 결제를 주/월 단위(:unit)로 묶어 합계한다. paidAt(실제 결제 시점) 기준이라
    // 정산 확정 여부와 무관하다 (SettlementStatsService 클래스 주석 참고). groupBuyCount는 그 구간에 결제가
    // 있었던 "서로 다른 공동구매" 건수 - COUNT(*)(참여자/결제 행 수)가 아니라 COUNT(DISTINCT group_buy_id)를
    // 쓴다(한 공동구매에 참여자가 여럿이어도 성사 건수는 1건). JPQL은 date_trunc를 지원하지 않아 네이티브
    // 쿼리로 작성 - 컬럼 별칭을 SettlementTrendRow의 getter 이름과 맞춘다
    @Query(value = """
            SELECT date_trunc(:unit, si.paid_at) AS "periodStart",
                   COALESCE(SUM(si.amount), 0) AS "totalSales",
                   COUNT(DISTINCT si.group_buy_id) AS "groupBuyCount"
            FROM settlement_item si
            WHERE si.producer_id = :producerId AND si.paid_at >= :from
            GROUP BY "periodStart"
            ORDER BY "periodStart"
            """, nativeQuery = true)
    List<SettlementTrendRow> findTrend(@Param("producerId") Long producerId, @Param("unit") String unit,
            @Param("from") LocalDateTime from);

    // 이번 달 요약 카드 - 기간 범위 매출 (상태 무관, [from, to) 반열림 구간)
    @Query("""
            SELECT COALESCE(SUM(si.amount), 0L) FROM SettlementItem si
            WHERE si.producerId = :producerId AND si.paidAt >= :from AND si.paidAt < :to
            """)
    long sumAmountByProducerIdAndPaidAtRange(@Param("producerId") Long producerId, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COUNT(si) FROM SettlementItem si
            WHERE si.producerId = :producerId AND si.paidAt >= :from AND si.paidAt < :to
            """)
    long countByProducerIdAndPaidAtRange(@Param("producerId") Long producerId, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // "정산 대기 중" 카드 - ACCRUED 전체 스냅샷 (월 무관, 기간 조건 없음)
    @Query("""
            SELECT COALESCE(SUM(si.amount), 0L) FROM SettlementItem si
            WHERE si.producerId = :producerId AND si.status = :status
            """)
    long sumAmountByProducerIdAndStatus(@Param("producerId") Long producerId,
            @Param("status") SettlementItemStatus status);

    long countByProducerIdAndStatus(Long producerId, SettlementItemStatus status);

    // "이번 달 정산 완료" 카드 - 이번 달 매출 중 이미 CONFIRMED까지 끝난 몫
    @Query("""
            SELECT COALESCE(SUM(si.amount), 0L) FROM SettlementItem si
            WHERE si.producerId = :producerId AND si.paidAt >= :from AND si.paidAt < :to AND si.status = :status
            """)
    long sumAmountByProducerIdAndPaidAtRangeAndStatus(@Param("producerId") Long producerId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("status") SettlementItemStatus status);

    @Query("""
            SELECT COUNT(si) FROM SettlementItem si
            WHERE si.producerId = :producerId AND si.paidAt >= :from AND si.paidAt < :to AND si.status = :status
            """)
    long countByProducerIdAndPaidAtRangeAndStatus(@Param("producerId") Long producerId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("status") SettlementItemStatus status);

    // "정산 대기중" 리스트 - ACCRUED 상태 settlement_item을 group_buy_id로 묶어 그룹바이 단위 미리보기 행을
    // 만든다. settlement 행이 아직 없는(=미확정) 공동구매만 나온다(ACCRUED가 남아있다는 것 자체가 그 뜻).
    // finalized_at 범위로 "그 달에 성사된 것"만 거른다 - SettlementQueryService 클래스 주석 참고.
    // GROUP BY + 페이지네이션이 같이 필요해 네이티브 쿼리로 작성. ORDER BY를 직접 쓰므로 호출 측은 정렬 없는
    // Pageable을 넘긴다(Sort를 얹으면 네이티브 쿼리에 자동 반영되지 않는다). 컬럼 별칭은
    // SettlementPendingRow의 getter 이름과 맞춘다
    @Query(value = """
            SELECT si.group_buy_id AS "groupBuyId", gb.title AS "groupBuyTitle", si.producer_id AS "producerId",
                   COUNT(*) AS "itemCount", SUM(si.amount) AS "totalSales"
            FROM settlement_item si
            JOIN group_buy gb ON gb.id = si.group_buy_id
            WHERE si.producer_id = :producerId AND si.status = 'ACCRUED'
              AND gb.finalized_at >= :from AND gb.finalized_at < :to
            GROUP BY si.group_buy_id, gb.title, si.producer_id, gb.finalized_at
            ORDER BY gb.finalized_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT si.group_buy_id
                FROM settlement_item si
                JOIN group_buy gb ON gb.id = si.group_buy_id
                WHERE si.producer_id = :producerId AND si.status = 'ACCRUED'
                  AND gb.finalized_at >= :from AND gb.finalized_at < :to
                GROUP BY si.group_buy_id
            ) sub
            """, nativeQuery = true)
    Page<SettlementPendingRow> findPendingByProducerIdAndFinalizedAtRange(@Param("producerId") Long producerId,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
