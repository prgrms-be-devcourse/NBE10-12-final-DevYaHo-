-- PG 승인 타임아웃/5xx 재시도가 소진됐을 때(실제 승인 여부 불명) 쓰는 과도 상태 (09-pg-timeout-retry.md)
ALTER TYPE payment_status ADD VALUE 'UNCONFIRMED';

-- UNCONFIRMED 건을 PaymentUnconfirmedReconciliationJob이 결제조회로 대조한 뒤 자동 재시도(1회)까지
-- 실패한 경우를 남긴다 (09-pg-timeout-retry.md)
ALTER TYPE payment_failure_type ADD VALUE 'APPROVAL_UNCONFIRMED_AFTER_TIMEOUT';

-- 여러 인스턴스가 같은 UNCONFIRMED 건을 동시에 재시도(실제 승인 호출)하지 않도록 선점 표시.
-- NULL이면 미점유, 값이 있으면 그 시각에 어떤 인스턴스가 처리를 시작했다는 뜻 (09-pg-timeout-retry.md)
ALTER TABLE payment ADD COLUMN reconciling_at TIMESTAMP;
