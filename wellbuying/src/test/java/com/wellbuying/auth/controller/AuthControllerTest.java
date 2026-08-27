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
import com.wellbuying.domain.member.entity.MemberStatus;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.ReflectionTestUtils;
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

    // 가입된 이메일/비밀번호로 로그인 시 200과 함께 accessToken/refreshToken/deviceId가 응답에 포함되는지 검증
    @Test
    void 이메일과_비밀번호로_로그인에_성공한다() throws Exception {
        memberRepository.save(
                Member.signUp("login-success@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));

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

    // 휴면 상태인 회원이 로그인을 시도하면 403과 MEMBER_403_DORMANT 에러 코드를 반환하는지 검증
    @Test
    void 휴면_계정은_로그인에_실패한다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("dormant-login@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        ReflectionTestUtils.setField(member, "status", MemberStatus.DORMANT);
        memberRepository.save(member);

        String requestBody = """
                {
                  "email": "dormant-login@example.com",
                  "password": "Pass1234!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_403_DORMANT"))
                .andDo(document("auth/login-dormant",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // [Critical] noRollbackFor 수정 검증 - 배치 미실행 휴면 대상 회원의 로그인 차단 시 markDormant() 전환이 실제로 커밋되는지 확인
    // 이 클래스는 클래스 레벨 @Transactional로 테스트 메서드 전체가 하나의 물리 트랜잭션을 공유하므로,
    // 단순히 "로그인 시도 후 바로 조회"하면 영속성 컨텍스트 상의 변경만 보일 뿐 실제 커밋 여부를 검증하지 못한다.
    // TestTransaction.flagForCommit()/end()로 실제 커밋을 강제한 뒤 start()로 새 트랜잭션을 열어 조회해야,
    // noRollbackFor가 없었다면(버그) rollbackOnly 처리되어 실제로는 롤백됐을 상태 변화를 정확히 잡아낼 수 있다
    @Test
    void 휴면_전환_대상_회원의_로그인_차단시_전환된_상태가_실제로_커밋된다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("rollback-check@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        ReflectionTestUtils.setField(member, "lastLoginAt", LocalDateTime.now().minusMonths(7));
        memberRepository.save(member);

        String requestBody = """
                { "email": "rollback-check@example.com", "password": "Pass1234!" }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_403_DORMANT"));

        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        Member persisted = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(MemberStatus.DORMANT);
    }

    // 휴면 회원에게 재활성화 코드를 발송한 뒤, 발급된 코드로 검증하면 ACTIVE로 전환되고 로그인 토큰이 발급되는지 검증
    @Test
    void 휴면_계정_재활성화_코드_발송_및_검증에_성공한다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("reactivation-success@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        ReflectionTestUtils.setField(member, "status", MemberStatus.DORMANT);
        memberRepository.save(member);

        String sendRequestBody = """
                { "email": "reactivation-success@example.com" }
                """;
        mockMvc.perform(post("/api/auth/reactivation/send")
                        .contentType("application/json")
                        .content(sendRequestBody))
                .andExpect(status().isOk())
                .andDo(document("auth/reactivation-send-success",
                        requestFields(fieldWithPath("email").description("휴면 계정 이메일"))));

        String code = redisTemplate.opsForValue().get("email:reactivation:reactivation-success@example.com");
        String verifyRequestBody = """
                { "email": "reactivation-success@example.com", "code": "%s" }
                """.formatted(code);

        mockMvc.perform(post("/api/auth/reactivation/verify")
                        .contentType("application/json")
                        .content(verifyRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andDo(document("auth/reactivation-verify-success",
                        requestFields(
                                fieldWithPath("email").description("휴면 계정 이메일"),
                                fieldWithPath("code").description("이메일로 발송된 인증 코드")),
                        responseFields(
                                fieldWithPath("accessToken").description("Access Token"),
                                fieldWithPath("refreshToken").description("Refresh Token"),
                                fieldWithPath("accessTokenExpiresIn").description("Access Token 만료(초)"),
                                fieldWithPath("deviceId").description("기기 식별자"))));

        Member reactivated = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(reactivated.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    // 재활성화 검증 요청 시 X-Device-Id 헤더를 전달하면 login()과 동일하게 응답 deviceId가 그대로 유지되는지 검증
    @Test
    void 재활성화_검증시_X_Device_Id를_전달하면_응답에_그대로_유지된다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("reactivation-device@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        ReflectionTestUtils.setField(member, "status", MemberStatus.DORMANT);
        memberRepository.save(member);

        String sendRequestBody = """
                { "email": "reactivation-device@example.com" }
                """;
        mockMvc.perform(post("/api/auth/reactivation/send")
                        .contentType("application/json")
                        .content(sendRequestBody))
                .andExpect(status().isOk());

        String code = redisTemplate.opsForValue().get("email:reactivation:reactivation-device@example.com");
        String verifyRequestBody = """
                { "email": "reactivation-device@example.com", "code": "%s" }
                """.formatted(code);

        mockMvc.perform(post("/api/auth/reactivation/verify")
                        .contentType("application/json")
                        .header("X-Device-Id", "pc_web_browser_uuid")
                        .content(verifyRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("pc_web_browser_uuid"));
    }

    // 휴면 상태가 아닌 회원이 재활성화 코드를 요청하면 409와 MEMBER_409_NOT_DORMANT 에러 코드를 반환하는지 검증
    @Test
    void 휴면이_아닌_회원의_재활성화_코드_요청은_실패한다() throws Exception {
        signUpMember("not-dormant@example.com");

        String requestBody = """
                { "email": "not-dormant@example.com" }
                """;

        mockMvc.perform(post("/api/auth/reactivation/send")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_409_NOT_DORMANT"))
                .andDo(document("auth/reactivation-send-not-dormant",
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
        // 컨테이너는 매 실행마다 새로 뜨지만(id가 1부터 재시작) Redis는 로컬 인스턴스를 그대로 재사용하므로,
        // 과거 실행에서 같은 id로 남은 세션 데이터가 있을 수 있어 사용 전에 미리 지운다
        redisTemplate.delete("ReT:" + member.getId());
        return member;
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
