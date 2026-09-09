-- phase24 §2-8: 실제 구현된 목록 조회(listActionLogs 등)는 target_type(+action IN)으로만 필터링하고
-- occurred_at DESC로 정렬한다. V36의 (target_type, target_id) 인덱스는 이 정렬을 커버하지 못해
-- 데이터가 쌓이면 정렬을 위한 별도 Sort 단계가 필요해진다 - 실제 쿼리 패턴에 맞는 인덱스를 추가한다.
-- (target_type, target_id)/(admin_id) 인덱스는 "특정 대상/관리자별 이력 조회" 용도로 남겨둔다.
CREATE INDEX idx_admin_action_log_target_type_occurred_at ON admin_action_log (target_type, occurred_at DESC);
