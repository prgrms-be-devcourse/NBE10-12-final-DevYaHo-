package com.wellbuying.auth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// Redis 장애(서킷 open) 시 refresh token을 임시로 보관하는 DB 폴백 저장소 - phase21 §2-3
@Entity
@Table(name = "refresh_token_fallback",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_refresh_token_fallback_member_device",
                columnNames = {"member_id", "device_id"}))
public class RefreshTokenFallbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "previous_token_hash")
    private String previousTokenHash;

    @Column(name = "grace_until")
    private Long graceUntil;

    @Column(name = "issued_at", nullable = false)
    private long issuedAt;

    @Column(name = "last_used_at", nullable = false)
    private long lastUsedAt;

    protected RefreshTokenFallbackEntity() {
    }

    private RefreshTokenFallbackEntity(Long memberId, String deviceId, RefreshTokenValue value) {
        this.memberId = memberId;
        this.deviceId = deviceId;
        applyValue(value);
    }

    public static RefreshTokenFallbackEntity create(Long memberId, String deviceId, RefreshTokenValue value) {
        return new RefreshTokenFallbackEntity(memberId, deviceId, value);
    }

    // rotate_refresh_token.lua와 동일한 검증 규칙(현재 토큰 또는 grace 기간 내 직전 토큰만 허용)을 DB에서 재현
    public boolean matches(String tokenHash, long now) {
        boolean isCurrent = this.tokenHash.equals(tokenHash);
        boolean isGracedPrevious = previousTokenHash != null && previousTokenHash.equals(tokenHash)
                && graceUntil != null && now <= graceUntil;
        return isCurrent || isGracedPrevious;
    }

    public void applyValue(RefreshTokenValue value) {
        this.tokenHash = value.tokenHash();
        this.previousTokenHash = value.previousTokenHash();
        this.graceUntil = value.graceUntil();
        this.issuedAt = value.issuedAt();
        this.lastUsedAt = value.lastUsedAt();
    }

    public String getTokenHash() {
        return tokenHash;
    }
}
