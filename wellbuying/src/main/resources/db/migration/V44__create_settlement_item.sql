-- 정산 대상 적립 (settlement 도메인 Phase 1)
-- 결제 완료 이벤트(payment-events / PaymentCompleted) 1건 = settlement_item 1행.
-- settlement 도메인이 payment-events를 구독해 실시간으로 쌓고(Phase 1),
-- 재결제 유예기간(성사일 + 3일)이 끝나면 배치가 group_buy 단위로 모아 확정한다(Phase 2).
-- 확정 집계행(settlement)과 플랫폼 수수료 계산은 Phase 2에서 도입한다.
CREATE TYPE settlement_item_status AS ENUM ('ACCRUED', 'CONFIRMED');

CREATE TABLE settlement_item (
    id                       BIGSERIAL              PRIMARY KEY,
    group_buy_id             BIGINT                 NOT NULL REFERENCES group_buy (id),
    group_buy_participant_id BIGINT                 NOT NULL REFERENCES group_buy_part (id),
    -- 판매자(공동구매 주최자). Phase 2에서 판매자 단위 정산 조회의 기준이 된다
    producer_id              BIGINT                 NOT NULL REFERENCES members (id),
    -- 구매자. 대사(對査)·추적용
    member_id                BIGINT                 NOT NULL REFERENCES members (id),
    -- 결제 원금. 수수료를 뺀 지급액 계산은 Phase 2 소관이라 여기서는 원금만 적재한다
    amount                   INTEGER                NOT NULL,
    -- 결제 승인 시각 (PaymentCompletedEvent.occurredAt) - 유예기간 계산 기준 후보이자 대사용
    paid_at                  TIMESTAMP              NOT NULL,
    status                   settlement_item_status NOT NULL DEFAULT 'ACCRUED',
    created_at               TIMESTAMP              NOT NULL DEFAULT now()
);

-- 한 참여 건은 정산에 한 번만 적립된다.
-- at-least-once 재수신의 2차 방어선 (payment.uk_payment_group_buy_participant_id와 같은 취지 -
-- 컨슈머의 사전 exists 확인을 나란히 통과한 동시 중복을 DB 제약으로 막는다)
CREATE UNIQUE INDEX uk_settlement_item_group_buy_participant_id ON settlement_item (group_buy_participant_id);

-- Phase 2 배치가 확정 대기(ACCRUED) 건을 group_buy 단위로 훑는다. 확정된(대다수) 행은 부분 인덱스에서 제외한다
CREATE INDEX idx_settlement_item_accrued ON settlement_item (group_buy_id) WHERE status = 'ACCRUED';
