-- 정산 확정 집계 (settlement 도메인 Phase 2)
-- 재결제 유예기간(공동구매 finalized_at + N일)이 끝나면 SettlementConfirmationWorker(@Scheduled)가
-- group_buy 단위로 settlement_item(ACCRUED)을 모아 이 집계행을 만들고, 해당 item들을 CONFIRMED로 전이한다.
-- 확정 이후 역정산/재조정은 하지 않는다 ("결제 완료 후 환불 불가" 정책 전제).
CREATE TYPE settlement_status AS ENUM ('CONFIRMED', 'PAID');

CREATE TABLE settlement (
    id           BIGSERIAL         PRIMARY KEY,
    group_buy_id BIGINT            NOT NULL REFERENCES group_buy (id),
    producer_id  BIGINT            NOT NULL REFERENCES members (id),
    -- 정산 대상 결제 건 수 (확정된 settlement_item 개수)
    item_count   INTEGER           NOT NULL,
    -- 총 매출 = 확정된 결제 원금(settlement_item.amount)의 합. 집계값이라 개별 결제(INTEGER)보다 커질 수 있어 BIGINT
    total_sales  BIGINT            NOT NULL,
    -- 플랫폼 수수료 = floor(total_sales * settlement.platform-fee-rate)
    platform_fee BIGINT            NOT NULL,
    -- 판매자 지급 예정액 = total_sales - platform_fee
    payout       BIGINT            NOT NULL,
    -- CONFIRMED(정산액 확정, 지급 대기)만 Phase 2에서 쓴다. PAID(실제 지급 완료)는 지급 실행 연동 시 도입
    status       settlement_status NOT NULL DEFAULT 'CONFIRMED',
    confirmed_at TIMESTAMP         NOT NULL,
    created_at   TIMESTAMP         NOT NULL DEFAULT now()
);

-- group_buy 하나당 정산은 한 번만 확정된다 (배치 재실행/동시 실행 시 2차 방어)
CREATE UNIQUE INDEX uk_settlement_group_buy_id ON settlement (group_buy_id);
-- 판매자별 정산 내역 조회용 (프런트 producer/settlements)
CREATE INDEX idx_settlement_producer_id ON settlement (producer_id);
