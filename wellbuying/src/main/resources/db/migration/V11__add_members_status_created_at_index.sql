-- findIdsForDormancy()의 lastLoginAt IS NULL 분기(createdAt 기준)가 인덱스를 타도록 (status, last_login_at)과 대칭되는 복합 인덱스 추가
CREATE INDEX idx_members_status_created_at ON members (status, created_at);
