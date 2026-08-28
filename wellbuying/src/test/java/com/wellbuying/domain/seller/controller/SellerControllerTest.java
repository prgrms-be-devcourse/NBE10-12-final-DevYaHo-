package com.wellbuying.domain.seller.controller;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.AuthenticatedMember;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import com.wellbuying.domain.seller.entity.SellerInfo;
import com.wellbuying.domain.seller.repository.SellerInfoRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Transactional
class SellerControllerTest extends AbstractIntegrationTest {

    private static final String EMAIL_VERIFIED_KEY_PREFIX = "email:verified:";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private SellerInfoRepository sellerInfoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 로그인한 회원이 은행/사업자 정보를 담아 셀러 신청 시 201을 반환하는지 검증
    @Test
    void 셀러_신청에_성공한다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("apply-success@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        String requestBody = """
                {
                  "bankCode": "088",
                  "bankName": "신한은행",
                  "accountNumber": "110-123-456789",
                  "accountHolder": "홍길동",
                  "companyName": "웰바잉스토어"
                }
                """;

        mockMvc.perform(post("/api/auth/seller/apply")
                        .with(authentication(authentication))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andDo(document("seller/apply-success",
                        requestFields(
                                fieldWithPath("bankCode").description("은행 코드"),
                                fieldWithPath("bankName").description("은행명"),
                                fieldWithPath("accountNumber").description("계좌번호"),
                                fieldWithPath("accountHolder").description("예금주"),
                                fieldWithPath("companyName").description("상호명").optional())));
    }

    // 이미 신청 이력이 있는 회원이 다시 신청하면 409와 SELLER_409_APPLICATION_EXISTS 에러 코드를 반환하는지 검증
    @Test
    void 이미_신청한_회원은_셀러_신청에_실패한다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("apply-duplicate@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        sellerInfoRepository.save(SellerInfo.apply(member.getId(), "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어"));
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        String requestBody = """
                {
                  "bankCode": "088",
                  "bankName": "신한은행",
                  "accountNumber": "110-123-456789",
                  "accountHolder": "홍길동",
                  "companyName": "웰바잉스토어"
                }
                """;

        mockMvc.perform(post("/api/auth/seller/apply")
                        .with(authentication(authentication))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_409_APPLICATION_EXISTS"))
                .andDo(document("seller/apply-duplicate",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 거절(REJECTED)된 회원이 다시 신청하면 201을 반환하고 기존 행이 PENDING으로 갱신되는지 검증
    @Test
    void 거절된_회원은_재신청에_성공한다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("reapply-success@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        SellerInfo sellerInfo = sellerInfoRepository.save(
                SellerInfo.apply(member.getId(), "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어"));
        sellerInfo.reject();
        sellerInfoRepository.save(sellerInfo);
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));
        String requestBody = """
                {
                  "bankCode": "004",
                  "bankName": "국민은행",
                  "accountNumber": "110-987-654321",
                  "accountHolder": "김철수",
                  "companyName": "웰바잉스토어2"
                }
                """;

        mockMvc.perform(post("/api/auth/seller/apply")
                        .with(authentication(authentication))
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated());

        SellerInfo updated = sellerInfoRepository.findById(sellerInfo.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getStatus())
                .isEqualTo(com.wellbuying.domain.seller.entity.SellerStatus.PENDING);
    }

    // 인증 정보 없이 셀러 신청 시 401과 AUTH_401_REQUIRED 에러 코드를 반환하는지 검증
    @Test
    void 인증되지_않은_요청은_셀러_신청에_실패한다() throws Exception {
        String requestBody = """
                {
                  "bankCode": "088",
                  "bankName": "신한은행",
                  "accountNumber": "110-123-456789",
                  "accountHolder": "홍길동",
                  "companyName": "웰바잉스토어"
                }
                """;

        mockMvc.perform(post("/api/auth/seller/apply")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_REQUIRED"))
                .andDo(document("seller/apply-unauthorized",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 이메일 인증 완료 후 다이렉트 셀러 가입 시 201과 함께 role이 BUYER로 응답되는지 검증
    @Test
    void 판매자_다이렉트_가입에_성공한다() throws Exception {
        redisTemplate.opsForValue().set(EMAIL_VERIFIED_KEY_PREFIX + "seller-signup-success@example.com", "1",
                Duration.ofMinutes(30));
        String requestBody = """
                {
                  "email": "seller-signup-success@example.com",
                  "password": "Pass1234!",
                  "name": "홍길동",
                  "bankCode": "088",
                  "bankName": "신한은행",
                  "accountNumber": "110-123-456789",
                  "accountHolder": "홍길동",
                  "companyName": "웰바잉스토어"
                }
                """;

        mockMvc.perform(post("/api/auth/seller/signup")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("seller-signup-success@example.com"))
                .andExpect(jsonPath("$.role").value("BUYER"))
                .andDo(document("seller/signup-success",
                        requestFields(
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("password").description("비밀번호 (8자 이상)"),
                                fieldWithPath("name").description("이름"),
                                fieldWithPath("bankCode").description("은행 코드"),
                                fieldWithPath("bankName").description("은행명"),
                                fieldWithPath("accountNumber").description("계좌번호"),
                                fieldWithPath("accountHolder").description("예금주"),
                                fieldWithPath("companyName").description("상호명").optional()),
                        responseFields(
                                fieldWithPath("memberId").description("회원 ID"),
                                fieldWithPath("email").description("이메일"),
                                fieldWithPath("name").description("이름"),
                                fieldWithPath("role").description("권한 (승인 전까지 BUYER)"))));
    }

    // 이메일 인증을 완료하지 않고 다이렉트 셀러 가입 시 403과 MEMBER_403_EMAIL_NOT_VERIFIED 에러 코드를 반환하는지 검증
    @Test
    void 이메일_인증을_완료하지_않으면_다이렉트_가입이_실패한다() throws Exception {
        String requestBody = """
                {
                  "email": "seller-not-verified@example.com",
                  "password": "Pass1234!",
                  "name": "홍길동",
                  "bankCode": "088",
                  "bankName": "신한은행",
                  "accountNumber": "110-123-456789",
                  "accountHolder": "홍길동",
                  "companyName": "웰바잉스토어"
                }
                """;

        mockMvc.perform(post("/api/auth/seller/signup")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_403_EMAIL_NOT_VERIFIED"))
                .andDo(document("seller/signup-email-not-verified",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 이미 가입된 이메일로 다이렉트 셀러 가입 시 409와 MEMBER_409_EMAIL_DUPLICATE 에러 코드를 반환하는지 검증
    @Test
    void 이메일이_중복되면_다이렉트_가입이_실패한다() throws Exception {
        memberRepository.save(
                Member.signUp("seller-duplicate@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        redisTemplate.opsForValue().set(EMAIL_VERIFIED_KEY_PREFIX + "seller-duplicate@example.com", "1",
                Duration.ofMinutes(30));
        String requestBody = """
                {
                  "email": "seller-duplicate@example.com",
                  "password": "Pass1234!",
                  "name": "홍길동",
                  "bankCode": "088",
                  "bankName": "신한은행",
                  "accountNumber": "110-123-456789",
                  "accountHolder": "홍길동",
                  "companyName": "웰바잉스토어"
                }
                """;

        mockMvc.perform(post("/api/auth/seller/signup")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_409_EMAIL_DUPLICATE"))
                .andDo(document("seller/signup-duplicate-email",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 신청 이력이 있는 회원이 내 셀러 신청 상태를 조회하면 200과 상태 정보를 반환하는지 검증
    @Test
    void 내_셀러_신청_상태_조회에_성공한다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("seller-info-success@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        sellerInfoRepository.save(
                SellerInfo.apply(member.getId(), "088", "신한은행", "110-123-456789", "홍길동", "웰바잉스토어"));
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));

        mockMvc.perform(get("/api/members/me/seller-info")
                        .with(authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andDo(document("seller/my-seller-info-success",
                        responseFields(
                                fieldWithPath("id").description("셀러 신청 ID"),
                                fieldWithPath("memberId").description("회원 ID"),
                                fieldWithPath("status").description("상태"),
                                fieldWithPath("bankName").description("은행명"),
                                fieldWithPath("companyName").description("사업자명").optional(),
                                fieldWithPath("createdAt").description("신청 일시"))));
    }

    // 신청 이력이 없는 회원이 내 셀러 신청 상태를 조회하면 404와 SELLER_404_NOT_FOUND를 반환하는지 검증
    @Test
    void 신청_이력이_없으면_내_셀러_신청_상태_조회에_실패한다() throws Exception {
        Member member = memberRepository.save(
                Member.signUp("seller-info-not-found@example.com", passwordEncoder.encode("Pass1234!"), "홍길동"));
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(member.getId(), "test-device"), null,
                List.of(new SimpleGrantedAuthority("ROLE_BUYER")));

        mockMvc.perform(get("/api/members/me/seller-info")
                        .with(authentication(authentication)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_404_NOT_FOUND"))
                .andDo(document("seller/my-seller-info-not-found",
                        responseFields(
                                fieldWithPath("code").description("에러 코드"),
                                fieldWithPath("message").description("에러 메시지"))));
    }

    // 인증 정보 없이 내 셀러 신청 상태 조회 시 401을 반환하는지 검증
    @Test
    void 인증되지_않은_요청은_내_셀러_신청_상태_조회에_실패한다() throws Exception {
        mockMvc.perform(get("/api/members/me/seller-info"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_REQUIRED"));
    }
}
