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

    // 운영 지표용 - pending/dead를 한 번의 쿼리로 조회해 두 Gauge가 같은 스냅샷을 읽도록 한다.
    // WHERE publishedAt IS NULL을 바깥에 둬서 idx_product_search_event_outbox_unpublished 인덱스를 타도록 함.
    // COALESCE: 조건에 맞는 행이 없을 때 SUM이 null을 반환해 생성자에서 NPE가 날 수 있으므로 방어.
    @Query("""
            SELECT new com.wellbuying.domain.product.search.OutboxStatusCount(
                COALESCE(SUM(CASE WHEN e.retryCount < :maxRetry THEN 1L ELSE 0L END), 0L),
                COALESCE(SUM(CASE WHEN e.retryCount >= :maxRetry THEN 1L ELSE 0L END), 0L)
            )
            FROM ProductSearchEventOutbox e
            WHERE e.publishedAt IS NULL
            """)
    OutboxStatusCount countStatusSnapshot(@Param("maxRetry") int maxRetry);

    // 릴레이가 배치를 ES에 반영한 뒤 성공한 건들의 published_at을 한 번의 UPDATE로 채운다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductSearchEventOutbox e SET e.publishedAt = :publishedAt WHERE e.id IN :ids")
    void markPublished(@Param("ids") List<Long> ids, @Param("publishedAt") LocalDateTime publishedAt);

    // 배치 반영 중 실패한 건들의 retryCount를 한 번의 UPDATE로 증가시킨다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductSearchEventOutbox e SET e.retryCount = e.retryCount + 1 WHERE e.id IN :ids")
    void incrementRetryCount(@Param("ids") List<Long> ids);
}
