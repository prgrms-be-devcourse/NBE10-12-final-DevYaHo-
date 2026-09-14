package com.wellbuying.auth.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class SecurityAuthorizationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    // --- Helper Methods ---

    private Member saveMember(String email, Role role) {
        Member member;
        if (role == Role.ADMIN) {
            member = Member.seedAdmin(email, "password", "AdminName");
        } else {
            member = Member.signUp(email, "password", "UserName", "010-1234-5678");
            if (role == Role.SELLER) {
                member.activateAsSeller();
            }
        }
        return memberRepository.save(member);
    }

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        // AuthenticatedMember는 memberId와 deviceId를 받습니다.
        AuthenticatedMember principal = new AuthenticatedMember(member.getId(), "test-device");
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()))
        );
    }

    // --- Tests ---

    @Test
    @DisplayName("관리자 전용 API(/api/admin/**)는 미인증 사용자가 접근하면 401(Unauthorized)을 반환한다")
    void adminApi_unauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 전용 API(/api/admin/**)는 일반 회원(BUYER)이 접근하면 403(Forbidden)을 반환한다")
    void adminApi_forbiddenForBuyer() throws Exception {
        Member buyer = saveMember("buyer@example.com", Role.BUYER);

        mockMvc.perform(get("/api/admin/members")
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 전용 API(/api/admin/**)는 셀러(SELLER)가 접근해도 403(Forbidden)을 반환한다")
    void adminApi_forbiddenForSeller() throws Exception {
        Member seller = saveMember("seller@example.com", Role.SELLER);

        mockMvc.perform(get("/api/admin/members")
                        .with(authentication(authOf(seller))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 전용 API(/api/admin/**)는 관리자(ADMIN)가 접근하면 허용된다")
    void adminApi_allowedForAdmin() throws Exception {
        Member admin = saveMember("admin@example.com", Role.ADMIN);

        mockMvc.perform(get("/api/admin/members")
                        .with(authentication(authOf(admin))))
                // 권한이 통과되어 실제 비즈니스 로직(Controller)까지 도달함을 의미 (401/403이 아니면 됨)
                // 실제 컨트롤러 응답에 따라 200이 반환될 것임
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증이 필요한 API(/api/groupBuys/mine)는 미인증 시 401(Unauthorized)을 반환한다")
    void authenticatedApi_unauthorized() throws Exception {
        mockMvc.perform(get("/api/groupBuys/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증이 필요한 API(/api/groupBuys/mine)는 회원(BUYER 등) 인증 시 허용된다")
    void authenticatedApi_allowed() throws Exception {
        Member buyer = saveMember("auth-buyer@example.com", Role.BUYER);

        mockMvc.perform(get("/api/groupBuys/mine")
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("공개 API(퍼블릭 GET API 등)는 미인증 사용자도 접근 가능하다")
    void publicApi_allowedForAnonymous() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }
}
