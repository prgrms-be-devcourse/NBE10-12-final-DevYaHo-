-- phase24 §2: 관리자 승인/거절/정지/정지복귀 액션의 공용 감사 이력 테이블.
-- target_id는 target_type(SELLER_INFO/PRODUCT/GROUP_BUY_SUSPENSION_REQUEST)마다 참조 테이블이 달라
-- 단일 FK를 걸 수 없어 polymorphic하게 둔다 - 참조 무결성은 애플리케이션이 보장한다.
CREATE TYPE admin_action_target_type AS ENUM ('SELLER_INFO', 'PRODUCT', 'GROUP_BUY_SUSPENSION_REQUEST');
CREATE TYPE admin_action_type AS ENUM ('APPROVE', 'REJECT', 'SUSPEND', 'REACTIVATE');

CREATE TABLE admin_action_log (
    id           BIGSERIAL PRIMARY KEY,
    target_type  admin_action_target_type NOT NULL,
    target_id    BIGINT NOT NULL,
    admin_id     BIGINT NOT NULL REFERENCES members (id),
    action       admin_action_type NOT NULL,
    reason       TEXT NOT NULL,
    occurred_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_action_log_target ON admin_action_log (target_type, target_id);
CREATE INDEX idx_admin_action_log_admin_id ON admin_action_log (admin_id);
