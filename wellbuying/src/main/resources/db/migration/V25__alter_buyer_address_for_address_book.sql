-- buyer_address는 V1__init_schema.sql에서 이미 만들어진 테이블이다. 회원이 재사용할 수 있는 배송지
-- 주소록으로, 공동구매 참여 건은 주소 텍스트를 스냅샷으로 복사하는 대신 이 테이블의 행을
-- buyer_address_id로 참조한다 (V26 참고). is_default는 V1 원래 설계 그대로 유지한다.
ALTER TABLE buyer_address RENAME COLUMN buyer_address TO address;
ALTER TABLE buyer_address RENAME COLUMN buyer_address_detail TO address_detail;
ALTER TABLE buyer_address RENAME COLUMN buyer_zipcode TO zipcode;
ALTER TABLE buyer_address ALTER COLUMN zipcode TYPE VARCHAR(20);
ALTER TABLE buyer_address ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE buyer_address ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT now();

CREATE INDEX idx_buyer_address_member_id ON buyer_address (member_id);
