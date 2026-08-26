CREATE TABLE product_category (
    id            BIGSERIAL PRIMARY KEY,
    parent_id     BIGINT REFERENCES product_category (id),
    category_name VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE product (
    id            BIGSERIAL PRIMARY KEY,
    seller_id     BIGINT       NOT NULL REFERENCES members (id),
    category_id   BIGINT       NOT NULL REFERENCES product_category (id),
    product_name  VARCHAR(200) NOT NULL,
    description   TEXT,
    start_price   INTEGER      NOT NULL,
    thumbnail_url VARCHAR(500),
    status        BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE product_count (
    product_id                 BIGINT PRIMARY KEY REFERENCES product (id),
    view_count                 BIGINT    NOT NULL DEFAULT 0,
    like_count                 BIGINT    NOT NULL DEFAULT 0,
    groupbuy_participant_count BIGINT    NOT NULL DEFAULT 0,
    updated_at                 TIMESTAMP NOT NULL DEFAULT now()
);
