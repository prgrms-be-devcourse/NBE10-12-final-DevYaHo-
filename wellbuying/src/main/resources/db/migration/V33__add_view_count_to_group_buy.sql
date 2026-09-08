ALTER TABLE group_buy ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;

-- 홈 화면 "인기" 섹션이 status='ONGOING'으로 필터링한 뒤 view_count DESC로 정렬해 조회한다
CREATE INDEX idx_group_buy_status_view_count ON group_buy (status, view_count DESC);
