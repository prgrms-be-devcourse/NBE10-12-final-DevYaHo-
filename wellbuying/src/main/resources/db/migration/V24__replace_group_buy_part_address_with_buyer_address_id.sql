-- 참여 시점 배송지를 텍스트 스냅샷 대신 회원 주소록(buyer_address) 참조로 저장하도록 변경한다.
-- 이후 회원이 주소록 항목을 수정하면 과거 참여 건에 표시되는 주소도 함께 바뀌는 것을 감수하고,
-- 재사용 가능한 주소록으로 전환한다. buyer_address_id는 기존 nullable 관례(테스트 픽스처 호환)를 그대로 따른다.
ALTER TABLE group_buy_part DROP COLUMN address;
ALTER TABLE group_buy_part DROP COLUMN address_detail;
ALTER TABLE group_buy_part DROP COLUMN zipcode;
ALTER TABLE group_buy_part ADD COLUMN buyer_address_id BIGINT REFERENCES buyer_address (id);
