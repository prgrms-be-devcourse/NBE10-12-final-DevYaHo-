package com.wellbuying.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.auth.jwt.TokenProvider;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.entity.Role;
import com.wellbuying.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

// phase16 트러블슈팅: /error forward 재진입으로 403/404/405가 401로 덮어써지던 버그의 회귀 검증
// MockMvc는 컨테이너 레벨 /error forward를 실제로 재현하지 않으므로(sendError만 기록되고 forward는 발생하지 않음),
// RANDOM_PORT로 실제 임베디드 서버를 띄우고 진짜 HTTP 호출로 검증한다
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class GlobalErrorControllerTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenProvider tokenProvider;

    private String issueAccessToken(String email, Role role) {
        Member member = memberRepository.save(Member.signUp(email, passwordEncoder.encode("Pass1234!"), "홍길동"));
        if (role != Role.BUYER) {
            ReflectionTestUtils.setField(member, "role", role);
            member = memberRepository.save(member);
        }
        return tokenProvider.createAccessToken(member.getId(), role, "test-device");
    }

    private HttpEntity<Void> bearerEntity(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    // BUYER 토큰으로 /api/admin/** 호출 시 401이 아닌 403 + COMMON_403_FORBIDDEN을 반환하는지 검증
    @Test
    void BUYER가_관리자_API를_호출하면_403을_반환한다() {
        String token = issueAccessToken("buyer-error-403@example.com", Role.BUYER);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/admin/sellers?status=PENDING", HttpMethod.GET, bearerEntity(token), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("COMMON_403_FORBIDDEN");
    }

    // 인증된 회원이 존재하지 않는 경로를 호출하면 401이 아닌 404 + COMMON_404_NOT_FOUND를 반환하는지 검증
    @Test
    void 존재하지_않는_경로를_호출하면_404를_반환한다() {
        String token = issueAccessToken("buyer-error-404@example.com", Role.BUYER);

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/no-such-path", HttpMethod.GET, bearerEntity(token), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("COMMON_404_NOT_FOUND");
    }

    // 존재하는 경로에 지원하지 않는 HTTP 메서드로 호출하면 401이 아닌 405 + COMMON_405_METHOD_NOT_ALLOWED를 반환하는지 검증
    @Test
    void 지원하지_않는_HTTP_메서드로_호출하면_405를_반환한다() {
        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/auth/login", ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo("COMMON_405_METHOD_NOT_ALLOWED");
    }
}
