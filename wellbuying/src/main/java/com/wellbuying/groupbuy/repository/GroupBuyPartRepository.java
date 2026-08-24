package com.wellbuying.groupbuy.repository;

import com.wellbuying.groupbuy.domain.GroupBuyPart;
import com.wellbuying.groupbuy.domain.GroupBuyPartStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupBuyPartRepository extends JpaRepository<GroupBuyPart, Long> {

    // 참여 취소 대상 조회 - 요청 경로의 groupBuyId와 partId가 일치하는지 함께 검증
    Optional<GroupBuyPart> findByIdAndGroupBuyId(Long id, Long groupBuyId);

    // 내 참여 내역 조회
    Optional<GroupBuyPart> findByGroupBuyIdAndMemberIdAndStatus(Long groupBuyId, Long memberId,
            GroupBuyPartStatus status);

    // 공동구매 성사 시 Kafka 이벤트를 참여자별로 개별 발행하기 위한 확정 참여자 목록 조회
    List<GroupBuyPart> findByGroupBuyIdAndStatus(Long groupBuyId, GroupBuyPartStatus status);

    // GroupBuyLifecycleScheduler가 이번 배치에서 성사 처리할 공동구매 전체의 확정 참여자를 한 번의 쿼리로 조회 (N+1 방지)
    List<GroupBuyPart> findByGroupBuyIdInAndStatus(List<Long> groupBuyIds, GroupBuyPartStatus status);

    // 실시간 상태 조회용 참여자 수 집계
    long countByGroupBuyIdAndStatus(Long groupBuyId, GroupBuyPartStatus status);
}
