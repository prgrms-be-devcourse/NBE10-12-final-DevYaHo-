-- phase21 §2-3: Redis 장애(서킷 open) 시 refresh token을 임시로 보관하는 DB 폴백 저장소.
-- 평소에는 비어 있으며, Redis 정상화 후 reissue() 시점에 자연스럽게 Redis로 이관되고 행이 삭제된다(§2-4).
CREATE TABLE refresh_token_fallback (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    device_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    previous_token_hash VARCHAR(255),
    grace_until BIGINT,
    issued_at BIGINT NOT NULL,
    last_used_at BIGINT NOT NULL,
    CONSTRAINT uk_refresh_token_fallback_member_device UNIQUE (member_id, device_id)
);
