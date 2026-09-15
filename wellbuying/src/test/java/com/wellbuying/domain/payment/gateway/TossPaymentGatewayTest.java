package com.wellbuying.domain.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// TossPaymentGateway는 성사 이벤트마다 실제로 실행되는 코드지만, PaymentProcessorTest 등에서는
// PaymentGateway 인터페이스 자체를 mock으로 대체해서 이 클래스의 진짜 코드(HTTP 호출ㆍ응답 파싱ㆍ
// 예외 변환ㆍ재시도)는 한 번도 실행된 적이 없었다. 실제 Toss 서버는 부르지 않고 MockRestServiceServer로
// HTTP 레벨만 가짜로 세워 계약을 고정한다. 재시도 백오프는 0ms로 줘서 테스트가 실제로 대기하지 않는다
class TossPaymentGatewayTest {

    private static final String BASE_URL = "https://api.tosspayments.com";

    private MockRestServiceServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentGateway(builder, BASE_URL, "test_sk_dummy", 3, 0);
    }

    private PgApproveCommand command() {
        return new PgApproveCommand("bk_123", "customer_1", "gb-order-1", "공동구매 결제", 10000, "GroupBuyCompleted:1");
    }

    @Test
    @DisplayName("승인 성공하면 paymentKey와 승인시각을 반환하고, Idempotency-Key 헤더를 실어 보낸다")
    void 승인_성공() {
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "GroupBuyCompleted:1"))
                .andRespond(withSuccess(
                        "{\"status\":\"DONE\",\"paymentKey\":\"pk_abc\",\"approvedAt\":\"2026-09-15T10:00:00+09:00\"}",
                        MediaType.APPLICATION_JSON));

        PgApproveResult result = gateway.approve(command());

        assertThat(result.pgTransactionId()).isEqualTo("pk_abc");
        assertThat(result.approvedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 10, 0, 0));
        server.verify();
    }

    @Test
    @DisplayName("PG가 DONE이 아닌 상태를 돌려주면 승인 거절로 보고 PgApprovalException을 던진다 (재시도 없음)")
    void 승인_거절() {
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andRespond(withSuccess("{\"status\":\"ABORTED\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.approve(command()))
                .isInstanceOf(PgApprovalException.class)
                .isNotInstanceOf(PgApprovalTimeoutException.class)
                .hasMessageContaining("status=ABORTED");
        server.verify();
    }

    @Test
    @DisplayName("4xx는 응답을 받은 명확한 거절이라 재시도 없이 바로 PgApprovalException을 던진다")
    void 사xx_거절_재시도_없음() {
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"code\":\"INVALID_REQUEST\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.approve(command()))
                .isInstanceOf(PgApprovalException.class)
                .isNotInstanceOf(PgApprovalTimeoutException.class);
        server.verify();
    }

    @Test
    @DisplayName("5xx는 애매한 응답으로 보고 같은 Idempotency-Key로 재시도하며, 재시도에서 성공하면 정상 승인된다")
    void 오xx는_재시도하고_성공하면_정상승인() {
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andExpect(header("Idempotency-Key", "GroupBuyCompleted:1"))
                .andRespond(withServerError());
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andExpect(header("Idempotency-Key", "GroupBuyCompleted:1"))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"paymentKey\":\"pk_retry\",\"approvedAt\":\"2026-09-15T10:00:00+09:00\"}",
                        MediaType.APPLICATION_JSON));

        PgApproveResult result = gateway.approve(command());

        assertThat(result.pgTransactionId()).isEqualTo("pk_retry");
        server.verify();
    }

    @Test
    @DisplayName("타임아웃/5xx가 재시도(3회) 끝까지 반복되면 PgApprovalTimeoutException을 던진다 - 승인 여부 불명")
    void 재시도_소진되면_타임아웃예외() {
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123")).andRespond(withServerError());
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123")).andRespond(withServerError());
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.approve(command()))
                .isInstanceOf(PgApprovalTimeoutException.class)
                .hasCauseInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    @DisplayName("승인시각 파싱에 실패해도 승인 자체는 성공 처리한다 - 시각은 표시용이라 결제를 막을 이유가 아니다")
    void 승인시각_파싱_실패해도_승인은_성공() {
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"paymentKey\":\"pk_abc\",\"approvedAt\":\"not-a-date\"}",
                        MediaType.APPLICATION_JSON));

        PgApproveResult result = gateway.approve(command());

        assertThat(result.pgTransactionId()).isEqualTo("pk_abc");
        assertThat(result.approvedAt()).isNotNull();
    }

    @Test
    @DisplayName("결제조회: DONE이면 승인된 것으로 판단하고 paymentKey를 담아 돌려준다")
    void 결제조회_승인됨() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/gb-order-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"DONE\",\"paymentKey\":\"pk_abc\",\"approvedAt\":\"2026-09-15T10:00:00+09:00\"}",
                        MediaType.APPLICATION_JSON));

        PgInquiryResult result = gateway.inquire("gb-order-1");

        assertThat(result.isApproved()).isTrue();
        assertThat(result.paymentKey()).isEqualTo("pk_abc");
        server.verify();
    }

    @Test
    @DisplayName("결제조회: 404(NOT_FOUND_PAYMENT)는 예외가 아니라 정상 조회 결과로 다룬다")
    void 결제조회_결제없음() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/gb-order-1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"code\":\"NOT_FOUND_PAYMENT\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        PgInquiryResult result = gateway.inquire("gb-order-1");

        assertThat(result.isApproved()).isFalse();
        assertThat(result.status()).isEqualTo("NOT_FOUND_PAYMENT");
    }

    @Test
    @DisplayName("결제조회 호출 자체가 실패하면(네트워크 오류 등) PgInquiryException을 던진다")
    void 결제조회_호출_실패() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/gb-order-1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.inquire("gb-order-1"))
                .isInstanceOf(PgInquiryException.class);
    }
}
