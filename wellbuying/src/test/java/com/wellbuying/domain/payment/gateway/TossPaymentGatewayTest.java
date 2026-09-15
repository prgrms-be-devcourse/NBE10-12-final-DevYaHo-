package com.wellbuying.domain.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// TossPaymentGateway는 성사 이벤트마다 실제로 실행되는 코드지만, PaymentProcessorTest 등에서는
// PaymentGateway 인터페이스 자체를 mock으로 대체해서 이 클래스의 진짜 코드(HTTP 호출ㆍ응답 파싱ㆍ
// 예외 변환)는 한 번도 실행된 적이 없었다. 실제 Toss 서버는 부르지 않고 MockRestServiceServer로
// HTTP 레벨만 가짜로 세워 계약을 고정한다.
class TossPaymentGatewayTest {

    private static final String BASE_URL = "https://api.tosspayments.com";

    private MockRestServiceServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentGateway(builder, BASE_URL, "test_sk_dummy");
    }

    @Test
    @DisplayName("승인 성공하면 paymentKey와 승인시각을 반환하고, Idempotency-Key 헤더를 실어 보낸다")
    void 승인_성공() {
        PgApproveCommand command = new PgApproveCommand("bk_123", "customer_1", "gb-order-1", "공동구매 결제", 10000,
                "GroupBuyCompleted:1");
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "GroupBuyCompleted:1"))
                .andRespond(withSuccess(
                        "{\"status\":\"DONE\",\"paymentKey\":\"pk_abc\",\"approvedAt\":\"2026-09-15T10:00:00+09:00\"}",
                        MediaType.APPLICATION_JSON));

        PgApproveResult result = gateway.approve(command);

        assertThat(result.pgTransactionId()).isEqualTo("pk_abc");
        assertThat(result.approvedAt()).isEqualTo(LocalDateTime.of(2026, 9, 15, 10, 0, 0));
        server.verify();
    }

    @Test
    @DisplayName("PG가 DONE이 아닌 상태를 돌려주면 승인 거절로 보고 PgApprovalException을 던진다")
    void 승인_거절() {
        PgApproveCommand command = new PgApproveCommand("bk_123", "customer_1", "gb-order-1", "공동구매 결제", 10000,
                "GroupBuyCompleted:1");
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andRespond(withSuccess("{\"status\":\"ABORTED\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.approve(command))
                .isInstanceOf(PgApprovalException.class)
                .hasMessageContaining("status=ABORTED");
    }

    @Test
    @DisplayName("PG 호출 자체가 실패하면(타임아웃ㆍ5xx 등) PgApprovalException으로 감싼다 - 실제 승인 여부는 알 수 없는 상태로 넘어간다")
    void 호출_자체_실패() {
        PgApproveCommand command = new PgApproveCommand("bk_123", "customer_1", "gb-order-1", "공동구매 결제", 10000,
                "GroupBuyCompleted:1");
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.approve(command))
                .isInstanceOf(PgApprovalException.class)
                .hasCauseInstanceOf(RestClientException.class);
    }

    @Test
    @DisplayName("승인시각 파싱에 실패해도 승인 자체는 성공 처리한다 - 시각은 표시용이라 결제를 막을 이유가 아니다")
    void 승인시각_파싱_실패해도_승인은_성공() {
        PgApproveCommand command = new PgApproveCommand("bk_123", "customer_1", "gb-order-1", "공동구매 결제", 10000,
                "GroupBuyCompleted:1");
        server.expect(requestTo(BASE_URL + "/v1/billing/bk_123"))
                .andRespond(withSuccess("{\"status\":\"DONE\",\"paymentKey\":\"pk_abc\",\"approvedAt\":\"not-a-date\"}",
                        MediaType.APPLICATION_JSON));

        PgApproveResult result = gateway.approve(command);

        assertThat(result.pgTransactionId()).isEqualTo("pk_abc");
        assertThat(result.approvedAt()).isNotNull();
    }
}
