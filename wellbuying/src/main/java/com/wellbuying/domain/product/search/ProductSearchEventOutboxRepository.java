package com.wellbuying.domain.product.search;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductSearchEventOutboxRepository extends JpaRepository<ProductSearchEventOutbox, Long> {

    // 아직 ES에 반영하지 못했고 재시도 한도(ProductSearchEventOutbox.MAX_RETRY_COUNT)를 넘지 않은 이벤트를
    // 오래된 순으로 조회 - 릴레이가 한 번의 실행에서 처리할 최대 건수를 limit으로 제한한다.
    // 재시도 한도를 넘긴 poison pill은 조회 대상에서 빠진다
    List<ProductSearchEventOutbox> findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(int retryCount, Limit limit);

    // 릴레이가 배치를 ES에 반영한 뒤 성공한 건들의 published_at을 한 번의 UPDATE로 채운다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductSearchEventOutbox e SET e.publishedAt = :publishedAt WHERE e.id IN :ids")
    void markPublished(@Param("ids") List<Long> ids, @Param("publishedAt") LocalDateTime publishedAt);

    // 배치 반영 중 실패한 건들의 retryCount를 한 번의 UPDATE로 증가시킨다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductSearchEventOutbox e SET e.retryCount = e.retryCount + 1 WHERE e.id IN :ids")
    void incrementRetryCount(@Param("ids") List<Long> ids);
}
