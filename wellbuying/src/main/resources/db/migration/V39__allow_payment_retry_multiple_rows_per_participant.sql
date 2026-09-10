-- 결제 실패 후 구매자가 직접 재시도하면(PaymentRetryService) 같은 group_buy_participant_id로
-- 새 Payment/Order 행을 만들고, 실패했던 행은 이력으로 남겨둔다. 그런데 V14의 유니크 인덱스는
-- 참여 건당 무조건 행 1개만 허용해서 재시도 시 첫 INSERT부터 유니크 제약 위반으로 실패한다.
--
-- "참여 건당 결제/주문은 하나만 존재한다"는 원래 의도(중복 Kafka 메시지 방어)는 유지하되,
-- 실패(FAILED/PAYMENT_FAILED)로 끝난 행은 그 제약에서 제외한다 - 정상 진행 중인 행은 여전히
-- 참여 건당 최대 1개로 강제되고, 실패 이력만 여러 개 쌓일 수 있다.
DROP INDEX uk_payment_group_buy_participant_id;
CREATE UNIQUE INDEX uk_payment_group_buy_participant_id ON payment (group_buy_participant_id)
    WHERE status <> 'FAILED';

DROP INDEX uk_orders_group_buy_participant_id;
CREATE UNIQUE INDEX uk_orders_group_buy_participant_id ON orders (group_buy_participant_id)
    WHERE status <> 'PAYMENT_FAILED';
