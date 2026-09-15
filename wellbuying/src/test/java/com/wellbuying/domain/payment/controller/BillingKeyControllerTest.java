package com.wellbuying.domain.payment.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.payment.entity.BillingKey;
import com.wellbuying.domain.payment.repository.BillingKeyRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// BillingKeyController의 세 DTO(BillingKeyAuthRequestResponse/BillingKeyResponse/BillingKeyRegisterRequest)는
// 다른 어떤 테스트에서도 생성된 적이 없어 커버리지가 0%였다. register() 성공 경로는 실제 Toss 네트워크
// 호출로 이어지므로(이미 TossBillingKeyClientTest에서 별도 검증), 여기서는 유효성 검증 실패 경로로만
// BillingKeyRegisterRequest를 생성해 PG 호출 없이 안전하게 검증한다.
@AutoConfigureMockMvc
@Transactional
class BillingKeyControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BillingKeyRepository billingKeyRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Member saveMember(String email) {
        return memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "홍길동"));
    }

    private UsernamePasswordAuthenticationToken authOf(Member member) {
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name())));
    }

    @Test
    void 발급_이력이_없으면_새_customerKey를_내려준다() throws Exception {
        Member buyer = saveMember("billingkey-new@example.com");

        mockMvc.perform(post("/api/payments/billing-key/auth-request")
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerKey").isNotEmpty());
    }

    // 카드를 교체해도 토스 쪽 고객이 갈라지지 않도록, 폐기된 건이라도 이전 customerKey를 재사용해야 한다
    @Test
    void 발급_이력이_있으면_폐기된_건이라도_그_customerKey를_재사용한다() throws Exception {
        Member buyer = saveMember("billingkey-reuse@example.com");
        BillingKey previous = billingKeyRepository.save(
                BillingKey.issued(buyer.getId(), "existing-customer-key", "encrypted", "국민", "1234"));
        previous.discard(LocalDateTime.now());
        billingKeyRepository.save(previous);

        mockMvc.perform(post("/api/payments/billing-key/auth-request")
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerKey").value("existing-customer-key"));
    }

    @Test
    void 등록된_카드가_없으면_registered_false를_반환한다() throws Exception {
        Member buyer = saveMember("billingkey-none@example.com");

        mockMvc.perform(get("/api/payments/billing-key")
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false));
    }

    @Test
    void 등록된_카드가_있으면_카드사와_뒷4자리를_반환한다() throws Exception {
        Member buyer = saveMember("billingkey-found@example.com");
        billingKeyRepository.save(BillingKey.issued(buyer.getId(), "customer-key", "encrypted", "신한", "5678"));

        mockMvc.perform(get("/api/payments/billing-key")
                        .with(authentication(authOf(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.cardCompany").value("신한"))
                .andExpect(jsonPath("$.cardLast4").value("5678"));
    }

    @Test
    void authKey가_없으면_토스를_호출하지_않고_400을_반환한다() throws Exception {
        Member buyer = saveMember("billingkey-invalid@example.com");
        String requestBody = """
                {
                  "authKey": "",
                  "customerKey": "customer-key"
                }
                """;

        mockMvc.perform(post("/api/payments/billing-key")
                        .with(authentication(authOf(buyer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
