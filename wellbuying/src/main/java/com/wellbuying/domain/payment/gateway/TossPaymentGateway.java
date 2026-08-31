package com.wellbuying.domain.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 토스페이먼츠 빌링키 자동결제 승인.
// 참여 시점에 발급해 둔 빌링키로 서버가 단독 승인하는 구조이며, 현재는 테스트 시크릿 키를 쓰므로 실제 출금은 없다
@Component
public class TossPaymentGateway implements PaymentGateway {

    private static final String PROVIDER = "TOSS";

    private final RestClient restClient;
    private final String authorizationHeader;

    public TossPaymentGateway(RestClient.Builder builder,
            @Value("${toss.base-url:https://api.tosspayments.com}") String baseUrl,
            @Value("${toss.secret-key:}") String secretKey) {
        this.restClient = builder.baseUrl(baseUrl).build();
        // 토스는 "시크릿키:" 를 Base64로 인코딩한 값을 Basic 인증에 쓴다 (비밀번호 자리는 비워둔다)
        this.authorizationHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public PgApproveResult approve(PgApproveCommand command) {
        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/v1/billing/{billingKey}", command.billingKey())
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    // 같은 키로 재요청하면 토스가 기존 승인 결과를 그대로 돌려준다 - 재수신 시 이중 결제를 막는 장치
                    .header("Idempotency-Key", command.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "customerKey", command.customerKey(),
                            "amount", command.amount(),
                            "orderId", command.orderId(),
                            "orderName", command.orderName()))
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException e) {
            // 타임아웃도 여기로 들어온다 - 실제 승인 여부를 알 수 없는 상태다 (01-consumer.md의 알려진 리스크)
            throw new PgApprovalException("토스 승인 호출 실패 - orderId=" + command.orderId(), e);
        }

        if (response == null) {
            throw new PgApprovalException("토스 승인 응답이 비어 있음 - orderId=" + command.orderId());
        }
        String status = (String) response.get("status");
        if (!"DONE".equals(status)) {
            throw new PgApprovalException("토스 승인 실패 - orderId=" + command.orderId() + ", status=" + status);
        }
        return new PgApproveResult((String) response.get("paymentKey"), parseApprovedAt(response.get("approvedAt")));
    }

    // 토스는 ISO-8601 오프셋 표기(2026-08-30T18:00:00+09:00)로 내려준다. 값이 없거나 형식이 다르면 수신 시각으로 대체한다
    private LocalDateTime parseApprovedAt(Object approvedAt) {
        if (approvedAt instanceof String text && !text.isBlank()) {
            try {
                return OffsetDateTime.parse(text).toLocalDateTime();
            } catch (RuntimeException ignored) {
                // 승인 자체는 성공했으므로 시각 파싱 실패로 결제를 되돌리지 않는다
            }
        }
        return LocalDateTime.now();
    }
}
