CREATE TYPE group_buy_suspension_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');

ALTER TABLE group_buy ADD COLUMN suspended BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE group_buy_suspension_request (
    id            BIGSERIAL PRIMARY KEY,
    group_buy_id  BIGINT NOT NULL REFERENCES group_buy (id),
    requester_id  BIGINT NOT NULL REFERENCES members (id),
    reason        TEXT,
    status        group_buy_suspension_status NOT NULL DEFAULT 'PENDING',
    requested_at  TIMESTAMP NOT NULL DEFAULT now(),
    decided_at    TIMESTAMP
);

CREATE INDEX idx_group_buy_suspension_request_status ON group_buy_suspension_request (status);
CREATE INDEX idx_group_buy_suspension_request_group_buy_id ON group_buy_suspension_request (group_buy_id);
