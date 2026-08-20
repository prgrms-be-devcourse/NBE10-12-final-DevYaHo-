package com.wellbuying.member.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 유효한 이메일/비밀번호/이름으로 회원가입 시 201과 함께 role이 BUYER로 응답되는지 검증
    @Test
    void 회원가입에_성공한다() throws Exception {
        String requestBody = """
                {
                  "email": "signup-success@example.com",
                  "password": "Pass1234!",
                  "name": "홍길동"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("signup-success@example.com"))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andDo(document("member/signup-success",
                        requestFields(
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("password").description("비밀번호 (8자 이상)"),
                                fieldWithPath("name").description("이름")),
                        responseFields(
                                fieldWithPath("memberId").description("회원 ID"),
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("name").description("이름"),
                                fieldWithPath("role").description("권한 (BUYER)"))));
    }

    // 이미 가입된 이메일로 회원가입 시 409와 MEMBER_409_EMAIL_DUPLICATE 에러 코드를 반환하는지 검증
    @Test
    void 이메일이_중복되면_회원가입에_실패한다() throws Exception {
        memberRepository.save(Member.signUp("duplicate@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));

        String requestBody = """
                {
                  "email": "duplicate@example.com",
                  "password": "Pass1234!",
                  "name": "홍길동"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_409_EMAIL_DUPLICATE"))
                .andDo(document("member/signup-duplicate-email",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 인증된 사용자(JWT 기반 Authentication)가 /api/members/me 호출 시 본인 정보를 응답받는지 검증
    @Test
    void 로그인한_회원은_내_정보를_조회한다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("me@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));

        mockMvc.perform(get("/api/members/me").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andDo(document("member/me-success",
                        responseFields(
                                fieldWithPath("memberId").description("회원 ID"),
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("name").description("이름"),
                                fieldWithPath("profileImageUrl").description("프로필 이미지 URL").optional(),
                                fieldWithPath("role").description("권한"))));
    }

    // 인증 정보 없이 /api/members/me 호출 시 401과 AUTH_401_REQUIRED 에러 코드를 반환하는지 검증
    @Test
    void 인증되지_않은_요청은_내_정보_조회에_실패한다() throws Exception {
        mockMvc.perform(get("/api/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_REQUIRED"))
                .andDo(document("member/me-unauthorized",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }
}
