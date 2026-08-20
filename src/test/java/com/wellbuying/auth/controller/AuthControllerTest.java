package com.wellbuying.auth.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.member.domain.Member;
import com.wellbuying.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 가입된 이메일/비밀번호로 로그인 시 200과 함께 accessToken/refreshToken/deviceId가 응답에 포함되는지 검증
    @Test
    void 이메일과_비밀번호로_로그인에_성공한다() throws Exception {
        memberRepository.save(Member.signUp("login-success@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));

        String requestBody = """
                {
                  "email": "login-success@example.com",
                  "password": "Pass1234!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .header("X-Device-Id", "pc_web_browser_uuid")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.deviceId").value("pc_web_browser_uuid"))
                .andDo(document("auth/login-success",
                        requestHeaders(
                                headerWithName("X-Device-Id").description("기기 식별자 (선택, 없으면 서버가 신규 발급)").optional()),
                        requestFields(
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("password").description("비밀번호")),
                        responseFields(
                                fieldWithPath("accessToken").description("Access Token"),
                                fieldWithPath("refreshToken").description("Refresh Token"),
                                fieldWithPath("accessTokenExpiresIn").description("Access Token 만료(초)"),
                                fieldWithPath("deviceId").description("기기 식별자"))));
    }

    // 비밀번호가 일치하지 않으면 401과 AUTH_401_INVALID_CREDENTIALS 에러 코드를 반환하는지 검증
    @Test
    void 비밀번호가_틀리면_로그인에_실패한다() throws Exception {
        memberRepository.save(Member.signUp("wrong-password@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));

        String requestBody = """
                {
                  "email": "wrong-password@example.com",
                  "password": "WrongPassword!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_INVALID_CREDENTIALS"))
                .andDo(document("auth/login-invalid-credentials",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 비밀번호가 없는(소셜 전용) 계정으로 일반 로그인 시 403과 AUTH_403_SOCIAL_ONLY 에러 코드를 반환하는지 검증
    @Test
    void 소셜_전용_계정은_일반_로그인에_실패한다() throws Exception {
        memberRepository.save(Member.socialOnly("social-only@example.com", "홍길동"));

        String requestBody = """
                {
                  "email": "social-only@example.com",
                  "password": "Pass1234!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_SOCIAL_ONLY"))
                .andDo(document("auth/login-social-only",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }
}
