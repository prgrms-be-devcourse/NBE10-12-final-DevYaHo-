-- 회원이 재사용할 수 있는 배송지 주소록. 공동구매 참여 건은 주소 텍스트를 스냅샷으로 복사하는 대신
-- 이 테이블의 행을 buyer_address_id로 참조한다 (V24 참고)
CREATE TABLE buyer_address (
    id             BIGSERIAL PRIMARY KEY,
    member_id      BIGINT       NOT NULL REFERENCES members (id),
    address        VARCHAR(255) NOT NULL,
    address_detail VARCHAR(255),
    zipcode        VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_buyer_address_member_id ON buyer_address (member_id);
