package com.wellbuying.auth.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.service.AuthService;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class AuthControllerTest extends AbstractIntegrationTest {

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

    // 로그인 시 lastLoginAt 갱신이 REQUIRES_NEW로 별도 커넥션/트랜잭션에서 일어나 테스트의 @Transactional 롤백 밖에서
    // 커밋되므로, commitFixture()로 강제 커밋한 회원 fixture는 자동 롤백되지 않는다 - 테스트 종료 시 직접 정리해야 함
    private final List<Long> committedMemberIds = new ArrayList<>();

    @AfterEach
    void cleanUpCommittedFixtures() {
        if (committedMemberIds.isEmpty()) {
            return;
        }
        // 직전 테스트 로직(예: reissue 실패 케이스)에서 발생한 예외로 현재 트랜잭션이 rollback-only로
        // 마킹돼 있을 수 있어, 그대로 커밋을 시도하면 UnexpectedRollbackException이 발생한다.
        // 일단 롤백해 마킹을 걷어낸 뒤 새 트랜잭션에서 삭제를 커밋한다.
        TestTransaction.end();
        TestTransaction.start();
        memberRepository.deleteAllById(committedMemberIds);
        committedMemberIds.forEach(id -> redisTemplate.delete("ReT:" + id));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        committedMemberIds.clear();
    }

    // 가입된 이메일/비밀번호로 로그인 시 200과 함께 accessToken/refreshToken/deviceId가 응답에 포함되는지 검증
    @Test
    void 이메일과_비밀번호로_로그인에_성공한다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("login-success@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        commitFixture(member);

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

    // 기기 목록 조회 시 로그인한 모든 기기의 deviceId/issuedAt/lastUsedAt이 반환되고 토큰 해시는 노출되지 않는지 검증
    @Test
    void 로그인_기기_목록을_조회한다() throws Exception {
        Member member = signUpMember("devices-list@example.com");
        login("devices-list@example.com", "device-1");
        login("devices-list@example.com", "device-2");

        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "device-1"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));

        mockMvc.perform(get("/api/auth/devices").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].deviceId").isNotEmpty())
                .andExpect(jsonPath("$[0].issuedAt").isNumber())
                .andExpect(jsonPath("$[0].lastUsedAt").isNumber())
                .andDo(document("auth/devices-success",
                        responseFields(
                                fieldWithPath("[].deviceId").description("기기 식별자"),
                                fieldWithPath("[].issuedAt").description("최초 로그인 시각 (epoch seconds)"),
                                fieldWithPath("[].lastUsedAt").description("마지막 사용 시각 (epoch seconds)"))));
    }

    // 로그인 세션이 하나도 없으면 빈 배열을 반환하는지 검증
    @Test
    void 로그인된_기기가_없으면_빈_목록을_반환한다() throws Exception {
        Member member = signUpMember("devices-empty@example.com");

        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "device-1"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));

        mockMvc.perform(get("/api/auth/devices").with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // 인증 정보 없이 기기 목록을 조회하면 401을 반환하는지 검증
    @Test
    void 인증되지_않은_요청은_기기_목록_조회에_실패한다() throws Exception {
        mockMvc.perform(get("/api/auth/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_REQUIRED"));
    }

    private Member signUpMember(String email) {
        Member member = memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "홍길동"));
        commitFixture(member);
        return member;
    }

    // 로그인 성공 시 lastLoginAt 갱신이 REQUIRES_NEW로 별도 커넥션/트랜잭션에서 일어나므로,
    // 테스트의 @Transactional 롤백 트랜잭션에 남아있는(아직 커밋 안 된) 회원 fixture는 그 별도 트랜잭션에서 보이지 않는다.
    // 따라서 로그인을 유발하는 테스트에서는 fixture 저장 직후 커밋해 별도 트랜잭션에서도 조회 가능하게 하고,
    // 커밋된 회원은 cleanUpCommittedFixtures()에서 직접 삭제해 다른 테스트로 데이터가 새지 않게 한다.
    private void commitFixture(Member member) {
        committedMemberIds.add(member.getId());
        // 컨테이너는 매 실행마다 새로 뜨지만(id가 1부터 재시작) Redis는 로컬 인스턴스를 그대로 재사용하므로,
        // 과거 실행에서 같은 id로 남은 세션 데이터가 있을 수 있어 사용 전에 미리 지운다
        redisTemplate.delete("ReT:" + member.getId());
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
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
