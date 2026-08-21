package com.wellbuying.auth.service;

import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.domain.SocialAccount;
import com.wellbuying.member.repository.MemberRepository;
import com.wellbuying.member.repository.SocialAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountService {

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;

    public OAuthAccountService(MemberRepository memberRepository, SocialAccountRepository socialAccountRepository) {
        this.memberRepository = memberRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    // (provider, providerId) 매칭 회원이 있으면 로그인, 동일 이메일의 기존 회원이 있으면 자동 연동, 둘 다 없으면 신규 생성
    @Transactional
    public Member findOrCreateMember(String provider, String providerId, String email, String name,
            String profileImage) {
        return socialAccountRepository.findByProviderAndProviderId(provider, providerId)
                .map(socialAccount -> memberRepository.findByIdAndDeletedAtIsNull(socialAccount.getMemberId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND)))
                .orElseGet(() -> linkOrCreateMember(provider, providerId, email, name, profileImage));
    }

    private Member linkOrCreateMember(String provider, String providerId, String email, String name,
            String profileImage) {
        return memberRepository.findByEmailAndDeletedAtIsNull(email)
                .map(member -> {
                    socialAccountRepository.save(SocialAccount.create(member.getId(), provider, providerId));
                    return member;
                })
                .orElseGet(() -> createMember(provider, providerId, email, name, profileImage));
    }

    // 탈퇴 회원의 이메일은 members.email 유니크 제약으로 재사용될 수 없어 unfiltered existsByEmail로 사전 차단
    private Member createMember(String provider, String providerId, String email, String name, String profileImage) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        Member member = Member.socialOnly(email, name);
        member.updateProfile(name, profileImage);
        Member savedMember = memberRepository.save(member);
        socialAccountRepository.save(SocialAccount.create(savedMember.getId(), provider, providerId));
        return savedMember;
    }
}
