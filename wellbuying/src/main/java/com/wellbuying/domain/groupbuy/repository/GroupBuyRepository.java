package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuyRepository extends JpaRepository<GroupBuy, Long>, GroupBuyQueryRepository {

    // 목록/검색 - 상태별 필터링 (카테고리 필터 없는 단순 조회 - repository 자체 테스트에서 사용)
    Page<GroupBuy> findByStatus(GroupBuyStatus status, Pageable pageable);

    // 관리자 공동구매 키워드 검색용 (제목 부분 일치)
    Page<GroupBuy> findByStatusAndTitleContainingIgnoreCase(GroupBuyStatus status, String keyword, Pageable pageable);

    // 관리자 공동구매 키워드 검색용 (상태 전체, 제목 부분 일치)
    Page<GroupBuy> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    // 상품 삭제 전 검증용 - 해당 상품에 지정된 상태의 공동구매가 하나라도 있는지 확인
    boolean existsByProductIdAndStatusIn(Long productId, List<GroupBuyStatus> statuses);

    // 검색 색인(OpenSearch) 배치 갱신용 - 여러 상품의 지정된 상태 공동구매를 한 번의 IN 쿼리로 조회 (상품 수만큼 개별 호출하지 않는다)
    List<GroupBuy> findByProductIdInAndStatusIn(List<Long> productIds, List<GroupBuyStatus> statuses);

    // 생산자별 조회 - GroupBuySeedRunner가 이전에 시딩한 자기 소유 데이터를 정리할 때 사용
    List<GroupBuy> findByProducerId(Long producerId);

    // 생산자별 목록 조회(페이징) - "내 공동구매" 화면용
    Page<GroupBuy> findByProducerId(Long producerId, Pageable pageable);

    // 생산자별 + 상태 필터 목록 조회(페이징)
    Page<GroupBuy> findByProducerIdAndStatus(Long producerId, GroupBuyStatus status, Pageable pageable);

    // 시작 시각이 지난 READY 공동구매 조회 - GroupBuyLifecycleScheduler가 ONGOING으로 전환할 대상
    // limit으로 한 번의 스케줄러 실행에서 처리할 최대 건수를 제한해 대량 적체 시에도 메모리 사용량을 예측 가능하게 유지한다
    // (처리되지 못한 나머지는 상태가 그대로 READY라 다음 실행에서 자연스럽게 이어서 처리된다)
    List<GroupBuy> findByStatusAndStartAtLessThanEqual(GroupBuyStatus status, LocalDateTime now, Limit limit);

    // 마감 시각이 지난 ONGOING 공동구매 조회 - GroupBuyLifecycleScheduler가 SUCCESS/FAILED로 확정할 대상 (limit 설명은 위와 동일)
    // 정렬 기준(end_at 오름차순)이 없으면 밀렸을 때(BATCH_LIMIT 초과) 어떤 순서로 처리될지 보장이 없어,
    // 먼저 마감된 건이 오히려 더 오래 기다릴 수 있다 - 가장 오래 대기 중인 건부터 처리되도록 명시한다
    List<GroupBuy> findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(GroupBuyStatus status, LocalDateTime now,
            Limit limit);

    // 성사(SUCCESS)됐지만 아직 참여자 최종가 반영/outbox 이벤트 기록이 안 된 건 조회 - GroupBuyFinalizationWorker의 처리 대상.
    // 트리거한 요청/틱은 status만 SUCCESS로 확정하고 곧바로 반환하며, 참여자 수(N)에 비례해 느려지는 무거운
    // 후속 작업은 이 워커가 별도 트랜잭션·별도 스케줄 틱에서 나눠 처리한다 (limit 설명은 위와 동일)
    List<GroupBuy> findByStatusAndFinalizedAtIsNullOrderByIdAsc(GroupBuyStatus status, Limit limit);

    // 참여 시 누적 수량을 원자적으로 증가시킨다 - "읽은 값 + delta를 자바에서 계산해 덮어쓰는" 방식이 아니라
    // DB가 직접 current_quantity = current_quantity + :quantity 를 한 문장으로 처리하므로, 동시에 여러 참여가
    // 몰려도 갱신이 유실(lost update)되지 않는다. clearAutomatically로 실행 후 영속성 컨텍스트를 비우므로
    // 호출 측은 최신 값이 필요하면 반드시 다시 조회해야 한다.
    // WHERE에 status/end_at 조건을 같이 걸어두는 이유: GroupBuyParticipationService.participate()가 검증
    // (STEP1)과 반영(STEP3)을 별도 트랜잭션으로 나누면서, 그 사이에 스케줄러 마감이나 관리자 판매정지로
    // 상태가 바뀔 수 있게 됐다 - 재-SELECT는 그 순간에도 또 바뀔 수 있어 완전한 방어가 안 되지만, 이
    // UPDATE 자체의 조건은 DB가 그 순간 원자적으로 확인하므로 확실하다. 조건에 안 맞으면 0건 반영되고
    // 호출 측이 반환값(영향받은 행 수)으로 실패를 판단한다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GroupBuy g SET g.currentQuantity = g.currentQuantity + :quantity "
            + "WHERE g.id = :id AND g.status = :status AND g.endAt > :now")
    int increaseQuantity(@Param("id") Long id, @Param("quantity") int quantity,
            @Param("status") GroupBuyStatus status, @Param("now") LocalDateTime now);

    // 참여 취소 시 누적 수량을 원자적으로 감소시킨다 (설명은 increaseQuantity와 동일). 0 미만으로는 내려가지 않는다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GroupBuy g SET g.currentQuantity = GREATEST(g.currentQuantity - :quantity, 0) WHERE g.id = :id")
    void decreaseQuantity(@Param("id") Long id, @Param("quantity") int quantity);

    // 상세 조회(GET /{id}) 시마다 조회수를 원자적으로 증가시킨다 - increaseQuantity와 동일한 이유로
    // 엔티티를 읽어 자바에서 +1 하는 대신 DB에 직접 반영한다(동시 조회가 몰려도 갱신 유실 없음)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GroupBuy g SET g.viewCount = g.viewCount + 1 WHERE g.id = :id")
    void increaseViewCount(@Param("id") Long id);
}
