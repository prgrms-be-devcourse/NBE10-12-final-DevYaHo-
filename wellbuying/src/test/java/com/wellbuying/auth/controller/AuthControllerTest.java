package com.wellbuying.auth.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.auth.service.AuthService;
import com.wellbuying.member.domain.Member;
import com.wellbuying.member.repository.MemberRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

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

    // 유효한 refresh token으로 재발급 요청 시 200과 함께 기존과 다른 새 access/refresh 토큰이 발급되는지 검증
    @Test
    void refresh_token으로_토큰_재발급에_성공한다() throws Exception {
        signUpMember("reissue-success@example.com");
        String refreshToken = login("reissue-success@example.com", "device-1");

        String requestBody = """
                { "refreshToken": "%s" }
                """.formatted(refreshToken);

        String response = mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andDo(document("auth/reissue-success",
                        requestFields(fieldWithPath("refreshToken").description("Refresh Token")),
                        responseFields(
                                fieldWithPath("accessToken").description("새로 발급된 Access Token"),
                                fieldWithPath("refreshToken").description("새로 발급된 Refresh Token"),
                                fieldWithPath("accessTokenExpiresIn").description("Access Token 만료(초)"))))
                .andReturn().getResponse().getContentAsString();

        String newRefreshToken = objectMapper.readTree(response).get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);
    }

    // 서명이 유효하지 않은 refresh token으로 재발급 요청 시 401과 AUTH_401_INVALID_TOKEN 에러 코드를 반환하는지 검증
    @Test
    void 유효하지_않은_refresh_token으로_재발급하면_실패한다() throws Exception {
        String requestBody = """
                { "refreshToken": "invalid.token.value" }
                """;

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_INVALID_TOKEN"))
                .andDo(document("auth/reissue-invalid-token",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // Redis에 세션이 없는(로그아웃되었거나 만료된) refresh token으로 재발급 요청 시 401과 AUTH_401_REFRESH_NOT_FOUND 에러 코드를 반환하는지 검증
    @Test
    void 세션이_없는_refresh_token으로_재발급하면_실패한다() throws Exception {
        Member member = signUpMember("reissue-no-session@example.com");
        String refreshToken = login("reissue-no-session@example.com", "device-1");
        redisTemplate.delete("ReT:" + member.getId());

        String requestBody = """
                { "refreshToken": "%s" }
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_REFRESH_NOT_FOUND"))
                .andDo(document("auth/reissue-session-not-found",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 이미 두 세대 전에 rotate된(재사용된) refresh token으로 재발급 요청 시 재사용 감지로 401과 함께 전체 세션이 삭제되는지 검증
    @Test
    void 재사용된_refresh_token으로_재발급하면_재사용_감지로_모든_세션이_삭제된다() throws Exception {
        signUpMember("reissue-reuse-detect@example.com");
        String originalRefreshToken = login("reissue-reuse-detect@example.com", "device-1");
        String requestBody = """
                { "refreshToken": "%s" }
                """.formatted(originalRefreshToken);

        mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk());

        // grace 기간 내 직전 토큰으로 재요청 - 정상 경쟁 요청으로 허용됨
        mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk());

        // 2세대 전 토큰으로 재요청 - 더 이상 유효 범위 밖 → 재사용 감지
        mockMvc.perform(post("/api/auth/reissue")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_REFRESH_REUSE_DETECTED"))
                .andDo(document("auth/reissue-reuse-detected",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 로그아웃 시 현재 기기(deviceId claim 기준)의 세션만 삭제되고 다른 기기 세션은 유지되는지 검증
    @Test
    void 로그아웃하면_현재_기기_세션만_삭제된다() throws Exception {
        Member member = signUpMember("logout-target@example.com");
        login("logout-target@example.com", "device-1");
        login("logout-target@example.com", "device-2");

        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "device-1"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));

        mockMvc.perform(post("/api/auth/logout").with(authentication(authentication)))
                .andExpect(status().isNoContent())
                .andDo(document("auth/logout-success"));

        assertThat(redisTemplate.opsForHash().hasKey("ReT:" + member.getId(), "device-1")).isFalse();
        assertThat(redisTemplate.opsForHash().hasKey("ReT:" + member.getId(), "device-2")).isTrue();
    }

    // 인증 정보 없이 로그아웃 요청 시 401과 AUTH_401_REQUIRED 에러 코드를 반환하는지 검증
    @Test
    void 인증되지_않은_요청은_로그아웃에_실패한다() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_REQUIRED"))
                .andDo(document("auth/logout-unauthorized",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 전체 로그아웃 시 회원의 모든 기기 세션이 삭제되는지 검증
    @Test
    void 전체_로그아웃하면_모든_기기_세션이_삭제된다() throws Exception {
        Member member = signUpMember("logout-all-target@example.com");
        login("logout-all-target@example.com", "device-1");
        login("logout-all-target@example.com", "device-2");

        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "device-1"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));

        mockMvc.perform(post("/api/auth/logout-all").with(authentication(authentication)))
                .andExpect(status().isNoContent())
                .andDo(document("auth/logout-all-success"));

        assertThat(redisTemplate.hasKey("ReT:" + member.getId())).isFalse();
    }

    // 소셜 로그인 성공 후 발급된 1회용 교환 코드로 access/refresh 토큰을 정상 교환하는지 검증
    @Test
    void 유효한_교환코드로_토큰을_교환한다() throws Exception {
        Member member = signUpMember("oauth-exchange-success@example.com");
        String code = authService.issueOAuthExchangeCode(member.getId(), member.getRole());

        String requestBody = """
                { "code": "%s" }
                """.formatted(code);

        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andDo(document("auth/oauth-exchange-success",
                        requestFields(fieldWithPath("code").description("소셜 로그인 콜백에서 발급받은 1회용 교환 코드")),
                        responseFields(
                                fieldWithPath("accessToken").description("Access Token"),
                                fieldWithPath("refreshToken").description("Refresh Token"),
                                fieldWithPath("accessTokenExpiresIn").description("Access Token 만료(초)"),
                                fieldWithPath("deviceId").description("기기 식별자"))));
    }

    // 존재하지 않거나 이미 사용된 교환 코드로 요청 시 401과 AUTH_401_OAUTH_CODE_INVALID 에러 코드를 반환하는지 검증
    @Test
    void 유효하지_않은_교환코드는_실패한다() throws Exception {
        String requestBody = """
                { "code": "invalid-code" }
                """;

        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_OAUTH_CODE_INVALID"))
                .andDo(document("auth/oauth-exchange-invalid-code",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    private Member signUpMember(String email) {
        return memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "홍길동"));
    }

    private String login(String email, String deviceId) throws Exception {
        String requestBody = """
                { "email": "%s", "password": "Pass1234!" }
                """.formatted(email);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .header("X-Device-Id", deviceId)
                        .content(requestBody))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("refreshToken").asText();
    }
}
