package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionRequest;
import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuySuspensionRequestRepository extends JpaRepository<GroupBuySuspensionRequest, Long> {

    // 관리자의 상태별 판매정지 요청 목록 조회
    Page<GroupBuySuspensionRequest> findAllByStatus(GroupBuySuspensionStatus status, Pageable pageable);

    // 공동구매 제목 검색용 - 요청 테이블 자체엔 제목이 없어 GroupBuy와 조인해 DB에서 한 번에 필터링한다.
    // (이전엔 제목 -> groupBuyId 목록을 애플리케이션 메모리로 먼저 가져온 뒤 IN 절로 다시 조회했는데,
    // 검색어가 넓으면 id 목록이 커지고 IN 절 파라미터가 늘어나 성능이 나빠질 수 있어 JOIN 한 번으로 합쳤다)
    @Query(value = """
            SELECT r FROM GroupBuySuspensionRequest r, GroupBuy g
            WHERE r.groupBuyId = g.id AND r.status = :status
              AND LOWER(g.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """,
            countQuery = """
            SELECT COUNT(r) FROM GroupBuySuspensionRequest r, GroupBuy g
            WHERE r.groupBuyId = g.id AND r.status = :status
              AND LOWER(g.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<GroupBuySuspensionRequest> findAllByStatusAndGroupBuyTitleContainingIgnoreCase(
            @Param("status") GroupBuySuspensionStatus status, @Param("keyword") String keyword, Pageable pageable);

    // 중복 요청 방지 - 같은 공동구매에 이미 처리 대기 중인 요청이 있는지 확인
    boolean existsByGroupBuyIdAndStatus(Long groupBuyId, GroupBuySuspensionStatus status);
}
