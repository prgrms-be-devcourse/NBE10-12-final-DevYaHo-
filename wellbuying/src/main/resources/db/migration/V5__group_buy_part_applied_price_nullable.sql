-- 참여 시점에는 가격을 계산/저장하지 않고, 공동구매가 성사되는 순간에만 최종가를 한 번 채운다.
-- 실패한 공동구매의 참여는 끝까지 null로 남아 "결제 대상이 아니다"라는 의미를 그대로 드러낸다.
ALTER TABLE group_buy_part ALTER COLUMN applied_price DROP NOT NULL;
