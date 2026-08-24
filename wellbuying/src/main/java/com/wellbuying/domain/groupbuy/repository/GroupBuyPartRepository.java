package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.domain.GroupBuyPart;
import com.wellbuying.domain.groupbuy.domain.GroupBuyPartStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // 공동구매 성사 시 확정 참여자 전원의 최종 단가를 한 문장으로 반영 - GroupBuyCloseProcessor가 건별 트랜잭션에서 호출하므로
    // 엔티티를 다시 조회/변경 감지에 태우지 않고 바로 반영해 N+1을 만들지 않는다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GroupBuyPart p SET p.appliedPrice = :finalPrice "
            + "WHERE p.groupBuyId = :groupBuyId AND p.status = :status")
    void applyFinalPriceToConfirmedParts(@Param("groupBuyId") Long groupBuyId, @Param("finalPrice") int finalPrice,
            @Param("status") GroupBuyPartStatus status);
}
