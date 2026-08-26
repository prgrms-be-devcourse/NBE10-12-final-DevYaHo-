package com.wellbuying.auth.service;

import com.wellbuying.auth.oauth.SocialLinkTicketRepository;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.SocialAccount;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.member.repository.SocialAccountRepository;
import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthAccountService {

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialLinkTicketRepository socialLinkTicketRepository;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public OAuthAccountService(MemberRepository memberRepository, SocialAccountRepository socialAccountRepository,
            SocialLinkTicketRepository socialLinkTicketRepository,
            ClientRegistrationRepository clientRegistrationRepository) {
        this.memberRepository = memberRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.socialLinkTicketRepository = socialLinkTicketRepository;
        this.clientRegistrationRepository = clientRegistrationRepository;
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
        member.updateProfile(name, profileImage, null);
        Member savedMember = memberRepository.save(member);
        socialAccountRepository.save(SocialAccount.create(savedMember.getId(), provider, providerId));
        return savedMember;
    }

    // 로그인 상태에서 소셜 계정을 추가 연동 - (provider, providerId)가 이미 다른 소셜 로그인으로 연동되어 있으면 거부
    // 이 회원이 동일 provider를 이미 연동한 경우도 거부 - providerId가 달라 위 체크는 통과해도 save 시 (member_id, provider) UNIQUE 제약(V3) 위반으로 DataIntegrityViolationException(500)이 발생하므로 사전 차단
    @Transactional
    public Member linkSocialAccount(Long memberId, String provider, String providerId) {
        String normalizedProvider = provider.toLowerCase();
        if (socialAccountRepository.findByProviderAndProviderId(normalizedProvider, providerId).isPresent()) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }
        if (socialAccountRepository.existsByMemberIdAndProvider(memberId, normalizedProvider)) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        socialAccountRepository.save(SocialAccount.create(memberId, normalizedProvider, providerId));
        return member;
    }

    // 회원이 연동한 소셜 계정 provider 목록 조회 (대문자 표기)
    @Transactional(readOnly = true)
    public List<String> getLinkedProviders(Long memberId) {
        return socialAccountRepository.findAllByMemberId(memberId).stream()
                .map(socialAccount -> socialAccount.getProvider().toUpperCase())
                .toList();
    }

    // 소셜 계정 연동 해제 - 비밀번호가 없고 연동된 소셜 계정이 이 하나뿐이면(마지막 로그인 수단) 해제 불가
    // 연동 목록을 한 번만 조회해 대상 존재 여부와 개수 체크를 함께 처리 (findByMemberIdAndProvider + countByMemberId 쿼리 2회 대신 1회)
    @Transactional
    public void unlinkSocialAccount(Long memberId, String provider) {
        String normalizedProvider = provider.toLowerCase();
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        List<SocialAccount> socialAccounts = socialAccountRepository.findAllByMemberId(memberId);
        SocialAccount socialAccount = socialAccounts.stream()
                .filter(account -> account.getProvider().equals(normalizedProvider))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NOT_FOUND));
        if (member.isSocialOnly() && socialAccounts.size() == 1) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_LAST_LOGIN_METHOD);
        }
        socialAccountRepository.delete(socialAccount);
    }

    // 로그인 상태에서 소셜 계정 연동을 시작하기 위한 1회용 link_token을 발급하고, provider 인가 엔드포인트 URL을 조립
    // provider는 registrationId(소문자)와 대조되므로, GET 목록 조회 응답(대문자)을 그대로 넘겨도 매칭되도록 정규화
    public String issueLinkRedirectUrl(Long memberId, String provider, String baseUrl) {
        String normalizedProvider = provider.toLowerCase();
        if (clientRegistrationRepository.findByRegistrationId(normalizedProvider) == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String linkToken = socialLinkTicketRepository.issue(memberId);
        return baseUrl + "/oauth2/authorization/" + normalizedProvider + "?link_token=" + linkToken;
    }
}
