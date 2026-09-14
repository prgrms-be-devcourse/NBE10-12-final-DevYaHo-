package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionRequest;
import com.wellbuying.domain.groupbuy.entity.GroupBuySuspensionStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuySuspensionRequestRepository extends JpaRepository<GroupBuySuspensionRequest, Long> {

    // 관리자의 상태별 판매정지 요청 목록 조회
    Page<GroupBuySuspensionRequest> findAllByStatus(GroupBuySuspensionStatus status, Pageable pageable);

    // 공동구매 제목 검색용 - 제목으로 찾은 공동구매 id 목록에 속하는 요청만 상태별로 조회
    Page<GroupBuySuspensionRequest> findAllByStatusAndGroupBuyIdIn(GroupBuySuspensionStatus status,
            Collection<Long> groupBuyIds, Pageable pageable);

    // 처리이력 탭 제목 검색용 - 공동구매 id 목록에 속하는 요청들의 id만 뽑아 admin_action_log.target_id 필터에 사용.
    // GroupBuyRepository.findIdByTitleContainingIgnoreCase와 같은 이유로 명시적 id select JPQL을 쓴다
    @Query("SELECT r.id FROM GroupBuySuspensionRequest r WHERE r.groupBuyId IN :groupBuyIds")
    List<Long> findIdByGroupBuyIdIn(@Param("groupBuyIds") Collection<Long> groupBuyIds);

    // 중복 요청 방지 - 같은 공동구매에 이미 처리 대기 중인 요청이 있는지 확인
    boolean existsByGroupBuyIdAndStatus(Long groupBuyId, GroupBuySuspensionStatus status);
}
