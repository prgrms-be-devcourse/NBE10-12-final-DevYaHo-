package com.wellbuying.domain.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 카드 인증(authKey)을 빌링키로 교환한다.
// 카드번호는 토스 결제창에서만 입력되므로 우리 서버는 authKey만 받으며, 카드 데이터에 닿지 않는다.
// 승인(TossPaymentGateway)과 같은 시크릿 키·같은 Basic 인증 방식을 쓴다
@Component
public class TossBillingKeyClient {

    private final RestClient restClient;
    private final String authorizationHeader;

    public TossBillingKeyClient(RestClient.Builder builder,
            @Value("${toss.base-url:https://api.tosspayments.com}") String baseUrl,
            @Value("${toss.secret-key:}") String secretKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.authorizationHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    public BillingKeyIssueResult issue(String authKey, String customerKey) {
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/v1/billing/authorizations/issue")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("authKey", authKey, "customerKey", customerKey))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException e) {
            throw new BillingKeyIssueException("토스 빌링키 발급 호출 실패", e);
        }

        if (response == null || response.get("billingKey") == null) {
            throw new BillingKeyIssueException("토스 빌링키 발급 응답에 billingKey가 없음");
        }
        String billingKey = (String) response.get("billingKey");
        return new BillingKeyIssueResult(billingKey, extractCardCompany(response), extractCardLast4(response));
    }

    // 응답의 card 객체 형태는 02-billingkey.md 조사 1번에서 확정할 항목이라, 없거나 형태가 달라도
    // 발급 자체를 실패시키지 않는다 (표시용 값이라 null이어도 결제는 정상 동작한다)
    @SuppressWarnings("unchecked")
    private Map<String, Object> card(Map<String, Object> response) {
        Object card = response.get("card");
        return card instanceof Map ? (Map<String, Object>) card : Map.of();
    }

    private String extractCardCompany(Map<String, Object> response) {
        Object issuer = card(response).get("issuerCode");
        return issuer instanceof String s && !s.isBlank() ? s : null;
    }

    // 토스는 카드번호를 마스킹해서 준다 (예: 12341234****123*). 뒤 4자리를 그대로 표시용으로 쓴다
    private String extractCardLast4(Map<String, Object> response) {
        Object number = card(response).get("number");
        if (number instanceof String s && s.length() >= 4) {
            return s.substring(s.length() - 4);
        }
        return null;
    }
}
