-- 빌링키 저장 (05-billingkey-issue.md)
-- 빌링키는 카드에 청구할 수 있는 재사용 자격증명이라 평문으로 두지 않는다.
-- 애플리케이션이 AES-256-GCM으로 암호화한 값을 넣으며, IV는 암호문 앞에 붙어 있다.
CREATE TABLE billing_key (
    id                    BIGSERIAL PRIMARY KEY,
    member_id             BIGINT      NOT NULL REFERENCES members (id),
    -- 토스 고객 식별자. 발급 때 쓴 값과 승인 때 보내는 값이 같아야 승인이 통과하므로 빌링키와 같은 행에 둔다.
    -- 회원 단위 값이라 카드를 교체해도 재사용되며, 폐기된 행과 값이 겹칠 수 있어 UNIQUE를 걸지 않는다
    customer_key          VARCHAR(64) NOT NULL,
    encrypted_billing_key TEXT        NOT NULL,
    -- 마이페이지에 "신한 ****1234"로 보여주기 위한 표시용 값 (발급 응답의 card 필드에서 받는다)
    card_company          VARCHAR(50),
    card_last4            VARCHAR(4),
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP   NOT NULL DEFAULT now(),
    -- 카드 교체 / 탈퇴 시 폐기 시각. 행을 지우지 않는 이유는 어떤 카드로 승인된 결제인지 추적이 남아야 하기 때문
    deleted_at            TIMESTAMP
);

-- 회원당 유효한 빌링키는 하나 (카드 교체 = 기존 행 폐기 후 새 행 삽입)
CREATE UNIQUE INDEX uk_billing_key_member_id_active ON billing_key (member_id) WHERE deleted_at IS NULL;
-- 폐기된 행까지 훑어 기존 customer_key를 재사용할 때 쓴다 (위 부분 인덱스로는 커버되지 않는다)
CREATE INDEX idx_billing_key_member_id ON billing_key (member_id);
