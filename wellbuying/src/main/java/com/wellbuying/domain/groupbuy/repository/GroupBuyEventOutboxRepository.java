package com.wellbuying.domain.groupbuy.repository;

import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupBuyEventOutboxRepository extends JpaRepository<GroupBuyEventOutbox, Long> {

    // 아직 Kafka로 발행하지 못한 이벤트를 오래된 순으로 조회 - GroupBuyOutboxRelay가 한 번의 실행에서
    // 처리할 최대 건수를 limit으로 제한한다(설명은 GroupBuyRepository의 동일 패턴과 같음)
    List<GroupBuyEventOutbox> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    void deleteByGroupBuyIdIn(List<Long> groupBuyIds);
}
