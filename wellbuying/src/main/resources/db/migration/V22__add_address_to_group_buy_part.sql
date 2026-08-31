-- 참여 시점의 배송지 스냅샷 - 이후 회원이 주소를 바꿔도 이미 참여한 건의 배송지는 그대로 유지되어야 하므로
-- 별도 주소록을 참조하지 않고 참여 건에 직접 저장한다. 기존 행에는 값이 없어 NOT NULL로 강제하지 않고,
-- 신규 참여부터는 애플리케이션(GroupBuyPartCreateRequest 검증)이 필수값으로 강제한다.
ALTER TABLE group_buy_part ADD COLUMN address VARCHAR(255);
ALTER TABLE group_buy_part ADD COLUMN address_detail VARCHAR(255);
ALTER TABLE group_buy_part ADD COLUMN zipcode VARCHAR(20);
