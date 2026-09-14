package com.wellbuying.domain.admin.repository;

import com.wellbuying.domain.admin.entity.AdminActionLog;
import com.wellbuying.domain.admin.entity.AdminActionTargetType;
import com.wellbuying.domain.admin.entity.AdminActionType;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {

    // 대상 유형별 감사 이력 조회 (상품 승인/거절, 공동구매 판매정지 승인/거절 등 - 대상당 액션이 2종류뿐인 화면)
    Page<AdminActionLog> findAllByTargetTypeOrderByOccurredAtDesc(AdminActionTargetType targetType,
            Pageable pageable);

    // 대상 유형 + 액션 범위별 감사 이력 조회 (셀러 전환 이력 vs. 셀러 정지/정지복귀 이력처럼 같은 대상이라도 화면을 분리해야 하는 경우)
    Page<AdminActionLog> findAllByTargetTypeAndActionInOrderByOccurredAtDesc(AdminActionTargetType targetType,
            Collection<AdminActionType> actions, Pageable pageable);

    // 제목 검색용 - target_id가 미리 조회해둔 후보 목록(예: 제목이 일치하는 공동구매의 판매정지 요청 id)에 속하는 이력만 조회
    Page<AdminActionLog> findAllByTargetTypeAndTargetIdInOrderByOccurredAtDesc(AdminActionTargetType targetType,
            Collection<Long> targetIds, Pageable pageable);
}
