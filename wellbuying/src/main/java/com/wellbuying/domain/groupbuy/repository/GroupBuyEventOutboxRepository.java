package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuyEventOutboxRepository extends JpaRepository<GroupBuyEventOutbox, Long> {

    // 아직 Kafka로 발행하지 못했고 재시도 한도(GroupBuyEventOutbox.MAX_RETRY_COUNT)를 넘지 않은 이벤트를
    // 오래된 순으로 조회 - GroupBuyOutboxRelay가 한 번의 실행에서 처리할 최대 건수를 limit으로 제한한다
    // (설명은 GroupBuyRepository의 동일 패턴과 같음). 재시도 한도를 넘긴 poison pill은 조회 대상에서 빠진다
    List<GroupBuyEventOutbox> findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(int retryCount, Limit limit);

    void deleteByGroupBuyIdIn(List<Long> groupBuyIds);

    // GroupBuyOutboxRelay가 배치를 병렬 발행한 뒤 성공한 건들의 published_at을 한 번의 UPDATE로 채운다 -
    // 건마다 개별 save()를 호출하면 그만큼 개별 UPDATE 왕복이 발생하므로 벌크로 묶는다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GroupBuyEventOutbox e SET e.publishedAt = :publishedAt WHERE e.id IN :ids")
    void markPublished(@Param("ids") List<Long> ids, @Param("publishedAt") LocalDateTime publishedAt);

    // 배치 발행 중 실패한 건들의 retryCount를 한 번의 UPDATE로 증가시킨다 (같은 이유로 벌크 처리)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE GroupBuyEventOutbox e SET e.retryCount = e.retryCount + 1 WHERE e.id IN :ids")
    void incrementRetryCount(@Param("ids") List<Long> ids);
}
