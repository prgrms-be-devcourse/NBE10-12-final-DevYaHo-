package com.wellbuying.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.SocialAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SocialAccountRepositoryTest {

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private MemberRepository memberRepository;

    // SocialAccount.create()로 만든 엔티티가 실제 DB에 저장되는지 검증
    @Test
    void 소셜계정을_저장한다() {
        Member member = memberRepository.save(Member.socialOnly("social@example.com", "홍길동"));

        SocialAccount saved = socialAccountRepository.save(SocialAccount.create(member.getId(), "google", "google-uid-1"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMemberId()).isEqualTo(member.getId());
    }

    // (provider, providerId)로 저장된 소셜계정을 정상 조회하는지 검증
    @Test
    void provider와_providerId로_소셜계정을_조회한다() {
        Member member = memberRepository.save(Member.socialOnly("social2@example.com", "홍길동"));
        socialAccountRepository.save(SocialAccount.create(member.getId(), "kakao", "kakao-uid-1"));

        assertThat(socialAccountRepository.findByProviderAndProviderId("kakao", "kakao-uid-1"))
                .isPresent()
                .get()
                .extracting(SocialAccount::getMemberId)
                .isEqualTo(member.getId());
    }

    // 존재하지 않는 (provider, providerId) 조합으로 조회하면 빈 Optional을 반환하는지 검증
    @Test
    void 존재하지_않는_소셜계정을_조회하면_빈값을_반환한다() {
        assertThat(socialAccountRepository.findByProviderAndProviderId("google", "none")).isEmpty();
    }
}
