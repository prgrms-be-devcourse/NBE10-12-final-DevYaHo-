package com.wellbuying.domain.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// TossBillingKeyClient도 TossPaymentGateway와 같은 이유로 테스트가 비어있었다(카드 등록마다 실행되는
// 코드인데, 다른 테스트에서는 항상 이 클래스 자체를 건너뛰고 결과만 스텁으로 준비했다).
class TossBillingKeyClientTest {

    private static final String BASE_URL = "https://api.tosspayments.com";

    private MockRestServiceServer server;
    private TossBillingKeyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossBillingKeyClient(builder, BASE_URL, "test_sk_dummy");
    }

    @Test
    @DisplayName("발급 성공하면 billingKey와 카드사ㆍ뒷 4자리를 반환한다")
    void 발급_성공() {
        server.expect(requestTo(BASE_URL + "/v1/billing/authorizations/issue"))
                .andRespond(withSuccess(
                        "{\"billingKey\":\"bk_abc\",\"card\":{\"issuerCode\":\"HYUNDAI\",\"number\":\"12341234****1234\"}}",
                        MediaType.APPLICATION_JSON));

        BillingKeyIssueResult result = client.issue("auth_key_1", "customer_1");

        assertThat(result.billingKey()).isEqualTo("bk_abc");
        assertThat(result.cardCompany()).isEqualTo("HYUNDAI");
        assertThat(result.cardLast4()).isEqualTo("1234");
    }

    @Test
    @DisplayName("카드 표시 정보가 없어도 billingKey만 있으면 발급은 성공한다 - 표시용 값이라 결제를 막을 이유가 아니다")
    void 카드_정보_없어도_발급_성공() {
        server.expect(requestTo(BASE_URL + "/v1/billing/authorizations/issue"))
                .andRespond(withSuccess("{\"billingKey\":\"bk_abc\"}", MediaType.APPLICATION_JSON));

        BillingKeyIssueResult result = client.issue("auth_key_1", "customer_1");

        assertThat(result.billingKey()).isEqualTo("bk_abc");
        assertThat(result.cardCompany()).isNull();
        assertThat(result.cardLast4()).isNull();
    }

    @Test
    @DisplayName("응답에 billingKey가 없으면 BillingKeyIssueException을 던진다")
    void billingKey_없으면_발급_실패() {
        server.expect(requestTo(BASE_URL + "/v1/billing/authorizations/issue"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.issue("auth_key_1", "customer_1"))
                .isInstanceOf(BillingKeyIssueException.class);
    }

    @Test
    @DisplayName("호출 자체가 실패하면 BillingKeyIssueException으로 감싼다")
    void 호출_자체_실패() {
        server.expect(requestTo(BASE_URL + "/v1/billing/authorizations/issue"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.issue("auth_key_1", "customer_1"))
                .isInstanceOf(BillingKeyIssueException.class)
                .hasCauseInstanceOf(org.springframework.web.client.RestClientException.class);
    }
}
