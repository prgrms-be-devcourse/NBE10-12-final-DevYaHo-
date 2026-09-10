CREATE TABLE payment_event_outbox (
    id            BIGSERIAL   PRIMARY KEY,
    group_buy_id  BIGINT      NOT NULL,
    event_type    VARCHAR(50) NOT NULL,
    payload       TEXT        NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    published_at  TIMESTAMP,
    retry_count   INT         NOT NULL DEFAULT 0
);

-- 릴레이가 매 주기 미발행 건만 폴링하므로, 이미 발행된(대다수) 행은 인덱스에서 아예 제외하는 부분 인덱스로 충분히 작게 유지한다.
-- group_buy_id에 FK를 걸지 않는다 - 결제 도메인이 groupbuy 테이블 스키마에 제약을 거는 결합을 피하고,
-- 발행 시점 스냅샷(payload)만 남기면 되기 때문 (product_search_event_outbox와 같은 방침)
CREATE INDEX idx_payment_event_outbox_unpublished ON payment_event_outbox (id) WHERE published_at IS NULL;
