CREATE TABLE group_buy_event_outbox (
    id            BIGSERIAL PRIMARY KEY,
    group_buy_id  BIGINT      NOT NULL REFERENCES group_buy (id),
    event_type    VARCHAR(50) NOT NULL,
    payload       TEXT        NOT NULL,
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    published_at  TIMESTAMP
);

-- 릴레이가 매 주기 미발행 건만 폴링하므로, 이미 발행된(대다수) 행은 인덱스에서 아예 제외하는 부분 인덱스로 충분히 작게 유지한다
CREATE INDEX idx_group_buy_event_outbox_unpublished ON group_buy_event_outbox (id) WHERE published_at IS NULL;
