package com.wellbuying.domain.admin.repository;

import com.wellbuying.domain.admin.entity.AdminActionLog;
import com.wellbuying.domain.admin.entity.AdminActionTargetType;
import com.wellbuying.domain.admin.entity.AdminActionType;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {

    // 대상 유형별 감사 이력 조회 (상품 승인/거절, 공동구매 판매정지 승인/거절 등 - 대상당 액션이 2종류뿐인 화면)
    Page<AdminActionLog> findAllByTargetTypeOrderByOccurredAtDesc(AdminActionTargetType targetType,
            Pageable pageable);

    // 대상 유형 + 액션 범위별 감사 이력 조회 (셀러 전환 이력 vs. 셀러 정지/정지복귀 이력처럼 같은 대상이라도 화면을 분리해야 하는 경우)
    Page<AdminActionLog> findAllByTargetTypeAndActionInOrderByOccurredAtDesc(AdminActionTargetType targetType,
            Collection<AdminActionType> actions, Pageable pageable);

    // 공동구매 판매정지 처리이력 전용 제목 검색 - admin_action_log(target_id=요청 id)는 제목을 갖고 있지
    // 않고, target_id/target_type은 여러 도메인을 가리키는 제네릭 컬럼이라 JPA 연관관계도 없다. 이전엔
    // 제목 -> groupBuyId 목록 -> requestId 목록을 애플리케이션에서 순차 조회해 IN 절로 넘겼는데, 검색어가
    // 넓으면 중간 id 목록이 커지는 문제가 있어 GroupBuySuspensionRequest/GroupBuy와 WHERE 등가조건으로
    // 조인해 DB에서 한 번에 필터링한다 (SettlementItemRepository의 settlement->groupbuy 조인과 같은 방식).
    // targetType 조건으로 반드시 이 유형만 걸러야 target_id가 다른 도메인 PK와 우연히 겹치는 사고를 막는다
    @Query(value = """
            SELECT a FROM AdminActionLog a, GroupBuySuspensionRequest r, GroupBuy g
            WHERE a.targetId = r.id AND r.groupBuyId = g.id
              AND a.targetType = :targetType
              AND LOWER(g.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY a.occurredAt DESC
            """,
            countQuery = """
            SELECT COUNT(a) FROM AdminActionLog a, GroupBuySuspensionRequest r, GroupBuy g
            WHERE a.targetId = r.id AND r.groupBuyId = g.id
              AND a.targetType = :targetType
              AND LOWER(g.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<AdminActionLog> findAllByTargetTypeAndGroupBuySuspensionRequestTitleContainingIgnoreCase(
            @Param("targetType") AdminActionTargetType targetType, @Param("keyword") String keyword,
            Pageable pageable);
}
