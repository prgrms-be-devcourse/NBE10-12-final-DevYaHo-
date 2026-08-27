CREATE TYPE member_status AS ENUM ('ACTIVE', 'DORMANT', 'WITHDRAWN');

ALTER TABLE members ADD COLUMN status member_status NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE members ALTER COLUMN status DROP DEFAULT;

ALTER TABLE members ADD COLUMN phone_number VARCHAR(20);
ALTER TABLE members ADD COLUMN last_login_at TIMESTAMP;

-- 이미 탈퇴 처리된(deleted_at이 채워진) 기존 회원을 신규 status 컬럼에도 반영
UPDATE members SET status = 'WITHDRAWN' WHERE deleted_at IS NOT NULL;

CREATE INDEX idx_members_status_last_login_at ON members (status, last_login_at);
