-- 결제 도메인 스키마 (payment / orders / payment_consumed_event)
-- 처리 순서: Payment 생성(READY) → PG 승인 → 성공 시 Order 생성
-- 이 순서 때문에 orders.payment_id는 NOT NULL이며 payment와 1:1이다.

CREATE TYPE payment_status AS ENUM ('READY', 'APPROVED', 'CANCELED', 'FAILED', 'REFUNDED');

-- ERD의 한글 상태값을 코드베이스 컨벤션(영문 ENUM)에 맞춰 매핑
-- 결제대기=PENDING, 결제완료=PAID, 결제실패=PAYMENT_FAILED, 배송준비=PREPARING,
-- 배송중=SHIPPING, 배송완료=DELIVERED, 구매확정=CONFIRMED, 취소=CANCELED
CREATE TYPE order_status AS ENUM (
    'PENDING', 'PAID', 'PAYMENT_FAILED', 'PREPARING',
    'SHIPPING', 'DELIVERED', 'CONFIRMED', 'CANCELED'
);

CREATE TABLE payment (
    id                       BIGSERIAL PRIMARY KEY,
    group_buy_participant_id BIGINT         NOT NULL REFERENCES group_buy_part (id),
    member_id                BIGINT         NOT NULL REFERENCES members (id),
    amount                   INTEGER        NOT NULL,
    pg_provider              VARCHAR(50)    NOT NULL,
    -- PG 승인 전(READY)에는 값이 없다. PostgreSQL UNIQUE는 NULL 중복을 허용하므로 그대로 UNIQUE 유지
    pg_transaction_id        VARCHAR(255) UNIQUE,
    -- PG 재요청 시 중복 승인을 막는 키. 참여 건당 1개로 생성한다
    idempotency_key          VARCHAR(255)   NOT NULL UNIQUE,
    status                   payment_status NOT NULL DEFAULT 'READY',
    approved_at              TIMESTAMP,
    canceled_at              TIMESTAMP,
    created_at               TIMESTAMP      NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP      NOT NULL DEFAULT now()
);

-- 한 참여 건에 대해 결제는 하나만 존재한다 (소비자 중복 수신 시 2차 방어선)
CREATE UNIQUE INDEX uk_payment_group_buy_participant_id ON payment (group_buy_participant_id);
CREATE INDEX idx_payment_member_id ON payment (member_id);
-- 미승인 상태로 남은 건 추적 / 정합성 대조 배치용
CREATE INDEX idx_payment_status_created_at ON payment (status, created_at);

CREATE TABLE orders (
    id                       BIGSERIAL PRIMARY KEY,
    payment_id               BIGINT       NOT NULL UNIQUE REFERENCES payment (id),
    group_buy_participant_id BIGINT       NOT NULL REFERENCES group_buy_part (id),
    member_id                BIGINT       NOT NULL REFERENCES members (id),
    status                   order_status NOT NULL DEFAULT 'PENDING',
    shipping_address         VARCHAR(500) NOT NULL,
    total_price              INTEGER      NOT NULL,
    confirmed_at             TIMESTAMP,
    created_at               TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_orders_group_buy_participant_id ON orders (group_buy_participant_id);
-- 내 주문 목록 조회 (최신순)
CREATE INDEX idx_orders_member_id_created_at ON orders (member_id, created_at);
-- 배송 도메인의 상태별 조회용
CREATE INDEX idx_orders_status ON orders (status);

-- Kafka 메시지 중복 수신 방어 (Outbox와는 별개 — 발행 측 보장은 03-outbox-poller.md에서 다룸)
CREATE TABLE payment_consumed_event (
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(255) NOT NULL UNIQUE,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP    NOT NULL DEFAULT now()
);

-- PG 승인은 성공했는데 그 결과를 DB에 반영하지 못한 건을 남긴다 (보상 트랜잭션 대신 채택한 방식 — 01-consumer.md 참고)
-- 실제 결제만 발생하고 기록이 없는 상태라 사람이 확인해 수동 처리해야 한다.
-- 기록 자체가 실패한 트랜잭션과 함께 롤백되면 안 되므로 항상 별도 트랜잭션(REQUIRES_NEW)으로 기록한다.
CREATE TYPE payment_failure_type AS ENUM ('APPROVE_RESULT_PERSIST_FAILED', 'ORDER_CREATE_FAILED');

CREATE TABLE payment_failure_log (
    id                       BIGSERIAL PRIMARY KEY,
    failure_type             payment_failure_type NOT NULL,
    event_id                 VARCHAR(255)         NOT NULL,
    group_buy_participant_id BIGINT               NOT NULL,
    member_id                BIGINT               NOT NULL,
    -- Payment 행 자체가 커밋되지 않았을 수 있으므로 FK를 걸지 않는다 (기록 실패가 재발하면 안 됨)
    payment_id               BIGINT,
    -- 수동 대사(對査)의 기준값 — PG에는 남아있는 승인 건을 찾는 열쇠
    pg_transaction_id        VARCHAR(255),
    amount                   INTEGER              NOT NULL,
    detail                   TEXT,
    resolved                 BOOLEAN              NOT NULL DEFAULT false,
    created_at               TIMESTAMP            NOT NULL DEFAULT now()
);

-- 미해결 건만 훑는 운영 조회용
CREATE INDEX idx_payment_failure_log_resolved_created_at ON payment_failure_log (resolved, created_at);
