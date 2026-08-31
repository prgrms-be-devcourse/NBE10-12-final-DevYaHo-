CREATE TABLE notification (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT       NOT NULL REFERENCES members (id),
    type         VARCHAR(50)  NOT NULL,
    group_buy_id BIGINT       NOT NULL REFERENCES group_buy (id),
    product_id   BIGINT,
    message      VARCHAR(200) NOT NULL,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

-- 회원별 최신순 목록 조회용
CREATE INDEX idx_notification_member_id_created_at ON notification (member_id, created_at DESC);

-- Kafka는 at-least-once라 이벤트가 재처리될 수 있음 - 같은 회원/공동구매/타입 조합의 중복 알림 생성을 막는다
CREATE UNIQUE INDEX uq_notification_member_group_buy_type ON notification (member_id, group_buy_id, type);
