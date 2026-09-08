-- 성사(SUCCESS) 확정 순간 참여자 전원의 최종가 반영 + outbox 이벤트 기록을 동기로 처리하던 것을
-- GroupBuyFinalizationWorker가 별도 스케줄러 틱에서 비동기로 처리하도록 분리한다. 트리거한 참여 요청/
-- 마감 스케줄러 틱이 참여자 수(N)에 비례해 느려지던 문제(부하테스트 실측: 300명 1.7s, 3,000명 6.2s)를
-- 없애기 위함 - status만 SUCCESS로 먼저 확정하고, 무거운 N명분 작업은 워커가 뒤이어 처리한다.
-- finalized_at이 NULL인 SUCCESS 건이 워커의 처리 대상. 기존 SUCCESS 건은 이미 최종가/이벤트가 반영된
-- 상태이므로 updated_at으로 백필해 워커가 다시 건드리지 않게 한다.
ALTER TABLE group_buy ADD COLUMN finalized_at TIMESTAMP;

UPDATE group_buy SET finalized_at = updated_at WHERE status = 'SUCCESS';

CREATE INDEX idx_group_buy_status_finalized_at ON group_buy (status, finalized_at);
