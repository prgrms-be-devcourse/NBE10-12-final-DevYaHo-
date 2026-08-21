package com.wellbuying.member.repository;

import com.wellbuying.member.domain.SocialAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    // (provider, providerId) 조합으로 연동된 소셜 계정 조회 - 소셜 로그인 시 기존 회원 매칭용
    Optional<SocialAccount> findByProviderAndProviderId(String provider, String providerId);
}
