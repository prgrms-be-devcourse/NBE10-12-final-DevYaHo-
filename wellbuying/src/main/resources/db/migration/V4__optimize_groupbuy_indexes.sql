-- findByGroupBuyIdAndStatus / countByGroupBuyIdAndStatus / findByGroupBuyIdInAndStatus가
-- group_buy_id 단독 인덱스보다 (group_buy_id, status) 복합 인덱스를 더 효율적으로 활용한다
DROP INDEX idx_group_buy_part_group_buy_id;
CREATE INDEX idx_group_buy_part_group_buy_id_status ON group_buy_part (group_buy_id, status);

-- GET /api/groupBuys 기본 목록 조회의 정렬 기준(createdAt desc)에 대한 인덱스
CREATE INDEX idx_group_buy_created_at ON group_buy (created_at);
