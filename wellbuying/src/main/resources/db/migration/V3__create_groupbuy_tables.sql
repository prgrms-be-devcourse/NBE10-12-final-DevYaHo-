CREATE TYPE group_buy_status AS ENUM ('READY', 'ONGOING', 'SUCCESS', 'FAILED', 'CANCELED');
CREATE TYPE group_buy_part_status AS ENUM ('PENDING', 'CONFIRMED', 'CANCELED');

CREATE TABLE group_buy (
    id               BIGSERIAL PRIMARY KEY,
    product_id       BIGINT           NOT NULL,
    producer_id      BIGINT           NOT NULL REFERENCES members (id),
    title            VARCHAR(200)     NOT NULL,
    status           group_buy_status NOT NULL DEFAULT 'READY',
    start_at         TIMESTAMP        NOT NULL,
    end_at           TIMESTAMP        NOT NULL,
    min_quantity     INT              NOT NULL,
    max_quantity     INT              NOT NULL,
    current_quantity INT              NOT NULL DEFAULT 0,
    created_at       TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP        NOT NULL DEFAULT now()
);

CREATE INDEX idx_group_buy_status_start_at ON group_buy (status, start_at);
CREATE INDEX idx_group_buy_status_end_at ON group_buy (status, end_at);

CREATE TABLE group_buy_price (
    id                 BIGSERIAL PRIMARY KEY,
    group_buy_id       BIGINT NOT NULL REFERENCES group_buy (id),
    tier_order         INT    NOT NULL,
    threshold_quantity INT    NOT NULL,
    unit_price         INT    NOT NULL,
    UNIQUE (group_buy_id, tier_order)
);

CREATE TABLE group_buy_part (
    id            BIGSERIAL PRIMARY KEY,
    group_buy_id  BIGINT                NOT NULL REFERENCES group_buy (id),
    member_id     BIGINT                NOT NULL REFERENCES members (id),
    quantity      INT                   NOT NULL,
    applied_price INT                   NOT NULL,
    status        group_buy_part_status NOT NULL,
    created_at    TIMESTAMP             NOT NULL DEFAULT now()
);

CREATE INDEX idx_group_buy_part_group_buy_id ON group_buy_part (group_buy_id);
CREATE INDEX idx_group_buy_part_member_id ON group_buy_part (member_id);
