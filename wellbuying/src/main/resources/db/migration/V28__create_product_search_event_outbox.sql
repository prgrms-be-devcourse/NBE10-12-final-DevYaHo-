CREATE TABLE product_search_event_outbox (
    id           BIGSERIAL   PRIMARY KEY,
    product_id   BIGINT      NOT NULL,
    event_type   VARCHAR(20) NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT now(),
    published_at TIMESTAMP,
    retry_count  INT         NOT NULL DEFAULT 0
);

-- 릴레이가 매 주기 미발행 건만 폴링하므로, 이미 발행된(대다수) 행은 인덱스에서 아예 제외하는 부분 인덱스로 충분히 작게 유지한다
CREATE INDEX idx_product_search_event_outbox_unpublished
    ON product_search_event_outbox (id) WHERE published_at IS NULL;
