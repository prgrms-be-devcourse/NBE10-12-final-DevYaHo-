package com.wellbuying.domain.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 토스페이먼츠 빌링키 자동결제 승인.
// 참여 시점에 발급해 둔 빌링키로 서버가 단독 승인하는 구조이며, 현재는 테스트 시크릿 키를 쓰므로 실제 출금은 없다.
// connect/read timeout은 이 클래스가 직접 설정하지 않고 RestClientCustomizer(TossRestClientConfig)가
// Spring이 주입하는 RestClient.Builder 빈에 적용한다 - 여기서 builder.requestFactory(...)를 직접 부르면
// 테스트가 MockRestServiceServer로 바인딩해 둔 같은 builder의 목 팩토리를 덮어써서 무력화되기 때문
// (09-pg-timeout-retry.md)
@Component
public class TossPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentGateway.class);

    private static final String PROVIDER = "TOSS";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_NOT_FOUND_PAYMENT = "NOT_FOUND_PAYMENT";

    private final RestClient restClient;
    private final String authorizationHeader;
    private final int approveMaxAttempts;
    private final long approveRetryBackoffMs;

    public TossPaymentGateway(RestClient.Builder builder,
            @Value("${toss.base-url:https://api.tosspayments.com}") String baseUrl,
            @Value("${toss.secret-key:}") String secretKey,
            @Value("${toss.approve-retry.max-attempts:3}") int approveMaxAttempts,
            @Value("${toss.approve-retry.backoff-ms:1000}") long approveRetryBackoffMs) {
        this.restClient = builder.baseUrl(baseUrl).build();
        // 토스는 "시크릿키:" 를 Base64로 인코딩한 값을 Basic 인증에 쓴다 (비밀번호 자리는 비워둔다)
        this.authorizationHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.approveMaxAttempts = approveMaxAttempts;
        this.approveRetryBackoffMs = approveRetryBackoffMs;
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public PgApproveResult approve(PgApproveCommand command) {
        RuntimeException lastAmbiguousFailure = null;
        for (int attempt = 1; attempt <= approveMaxAttempts; attempt++) {
            try {
                return requestApproval(command);
            } catch (HttpClientErrorException e) {
                // 4xx - 토스가 요청을 받아 명확히 거절한 것. 재시도해도 같은 결과라 바로 확정한다
                throw new PgApprovalException(
                        "토스 승인 거절 - orderId=" + command.orderId() + ", status=" + e.getStatusCode(), e);
            } catch (RestClientException e) {
                // 타임아웃/커넥션 오류/5xx 등 - 실제로 처리됐는지 알 수 없는 애매한 응답.
                // 같은 Idempotency-Key로 재요청해야 이중 승인을 피할 수 있다
                lastAmbiguousFailure = e;
                log.warn("토스 승인 응답 불명(재시도 {}/{}) - orderId={}", attempt, approveMaxAttempts, command.orderId(), e);
                if (attempt < approveMaxAttempts) {
                    sleepBeforeRetry();
                }
            }
        }
        throw new PgApprovalTimeoutException(
                "토스 승인 응답을 " + approveMaxAttempts + "회 재시도 끝에도 받지 못함 - orderId=" + command.orderId(),
                lastAmbiguousFailure);
    }

    @Override
    public PgInquiryResult inquire(String orderId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/v1/payments/orders/{orderId}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (response == null) {
                throw new PgInquiryException("토스 결제조회 응답이 비어 있음 - orderId=" + orderId, null);
            }
            return new PgInquiryResult((String) response.get("status"), (String) response.get("paymentKey"),
                    parseApprovedAt(response.get("approvedAt")));
        } catch (HttpClientErrorException.NotFound e) {
            // NOT_FOUND_PAYMENT - 토스에 접수조차 안 된 것도 정상적인 조회 결과로 다룬다 (예외 아님)
            return new PgInquiryResult(STATUS_NOT_FOUND_PAYMENT, null, null);
        } catch (RestClientException e) {
            throw new PgInquiryException("토스 결제조회 호출 실패 - orderId=" + orderId, e);
        }
    }

    private PgApproveResult requestApproval(PgApproveCommand command) {
        Map<String, Object> response = restClient.post()
                .uri("/v1/billing/{billingKey}", command.billingKey())
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                // 같은 키로 재요청하면 토스가 기존 승인 결과를 그대로 돌려준다 - 재수신/재시도 시 이중 결제를 막는 장치
                .header("Idempotency-Key", command.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "customerKey", command.customerKey(),
                        "amount", command.amount(),
                        "orderId", command.orderId(),
                        "orderName", command.orderName()))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        if (response == null) {
            throw new PgApprovalException("토스 승인 응답이 비어 있음 - orderId=" + command.orderId());
        }
        String status = (String) response.get("status");
        if (!STATUS_DONE.equals(status)) {
            throw new PgApprovalException("토스 승인 실패 - orderId=" + command.orderId() + ", status=" + status);
        }
        return new PgApproveResult((String) response.get("paymentKey"), parseApprovedAt(response.get("approvedAt")));
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(approveRetryBackoffMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PgApprovalTimeoutException("승인 재시도 대기 중 인터럽트 발생 - 승인 여부 불명", e);
        }
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
