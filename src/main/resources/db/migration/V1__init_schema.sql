CREATE TYPE member_role AS ENUM ('ADMIN', 'SELLER', 'BUYER');
CREATE TYPE seller_status AS ENUM ('PENDING', 'ACTIVE', 'SUSPENDED', 'TERMINATED');
CREATE TYPE seller_rank AS ENUM ('SILVER', 'GOLD', 'DIAMOND');
CREATE TYPE settlement_cycle AS ENUM ('DAILY', 'WEEKLY', 'MONTHLY');

CREATE TABLE members (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    password      VARCHAR(255),
    role          member_role  NOT NULL,
    profile_image VARCHAR(255),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMP
);

CREATE TABLE social_account (
    id          BIGSERIAL PRIMARY KEY,
    member_id   BIGINT       NOT NULL REFERENCES members (id),
    provider    VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_id)
);

CREATE TABLE seller_info (
    id               BIGSERIAL PRIMARY KEY,
    member_id        BIGINT           NOT NULL REFERENCES members (id),
    bank_code        INT              NOT NULL,
    bank_name        VARCHAR(255)     NOT NULL,
    account_number   VARCHAR(255)     NOT NULL,
    account_holder   VARCHAR(255)     NOT NULL,
    company_name     VARCHAR(255),
    status           seller_status    NOT NULL DEFAULT 'PENDING',
    rank             seller_rank      NOT NULL,
    settlement_cycle settlement_cycle NOT NULL,
    approved_at      TIMESTAMP,
    created_at       TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP        NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMP
);

CREATE TABLE buyer_address (
    id                    BIGSERIAL PRIMARY KEY,
    member_id             BIGINT       NOT NULL REFERENCES members (id),
    buyer_address         VARCHAR(255) NOT NULL,
    buyer_address_detail  VARCHAR(255),
    buyer_zipcode         VARCHAR(255) NOT NULL,
    is_default            BOOLEAN      NOT NULL DEFAULT false
);
