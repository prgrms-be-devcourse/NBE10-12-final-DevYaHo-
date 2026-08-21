package com.wellbuying.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "social_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_social_account_provider_provider_id",
                columnNames = {"provider", "provider_id"}))
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SocialAccount() {
    }

    private SocialAccount(Long memberId, String provider, String providerId) {
        this.memberId = memberId;
        this.provider = provider;
        this.providerId = providerId;
    }

    // 회원-소셜계정 연동 생성 - (provider, providerId) 조합은 유니크 제약으로 보호됨
    public static SocialAccount create(Long memberId, String provider, String providerId) {
        return new SocialAccount(memberId, provider, providerId);
    }

    public Long getId() {
        return id;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }
}
