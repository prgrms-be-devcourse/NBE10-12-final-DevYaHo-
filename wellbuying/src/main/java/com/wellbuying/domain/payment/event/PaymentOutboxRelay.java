package com.wellbuying.domain.payment.event;

import com.wellbuying.domain.payment.entity.PaymentEventOutbox;
import com.wellbuying.domain.payment.repository.PaymentEventOutboxRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 아웃박스에 쌓인 미발행 결제 이벤트를 주기적으로 폴링해 payment-events 토픽으로 발행한다.
// 상태 전이(PAID/PAYMENT_FAILED)와 아웃박스 기록은 PaymentEventPublisher가 이미 같은 트랜잭션으로 묶어뒀으므로,
// 여기서 Kafka 발행이 잠시 실패하거나 이 프로세스가 죽어도 이벤트 자체는 DB에 남아 다음 주기에 재시도된다.
// (배치·재시도 정책은 GroupBuyOutboxRelay와 동일 - 상세 주석은 그쪽 참고)
@Component
public class PaymentOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxRelay.class);

    private static final String TOPIC = "payment-events";
    private static final long SEND_TIMEOUT_SECONDS = 5;

    // 한 번의 라운드에서 처리할 최대 건수 - 예측 가능한 메모리 사용을 위해 제한한다
    // (처리 못한 나머지는 published_at이 그대로 null이라 다음 라운드/틱에서 자연스럽게 이어서 처리됨)
    private static final Limit BATCH_LIMIT = Limit.of(200);

    // 한 틱(3초) 안에서 최대 몇 라운드까지 이어서 뺄지 - 백로그가 있는 동안은 다음 @Scheduled 틱을 기다리지 않고
    // 곧바로 다음 라운드를 돈다. 상한을 두는 이유는 브로커가 계속 느리게 응답할 때 이 스케줄러 스레드가
    // 다음 틱들을 영영 못 돌게 독점하는 걸 막기 위함
    private static final int MAX_ROUNDS_PER_TICK = 10;

    private final PaymentEventOutboxRepository outboxRepository;
    private final PaymentOutboxDispatcher dispatcher;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentOutboxRelay(PaymentEventOutboxRepository outboxRepository, PaymentOutboxDispatcher dispatcher,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.dispatcher = dispatcher;
        this.kafkaTemplate = kafkaTemplate;
    }

    // 백로그가 BATCH_LIMIT보다 많이 쌓여있으면 다음 3초 틱을 기다리지 않고 이 틱 안에서 곧바로 다음 라운드를 이어서 뺀다.
    // 백로그가 없을 땐 라운드 1번(최대 200건) 확인하고 끝난다
    @Scheduled(fixedDelay = 3_000)
    public void relay() {
        for (int round = 0; round < MAX_ROUNDS_PER_TICK; round++) {
            int processed = relayOnce();
            if (processed < BATCH_LIMIT.max()) {
                return;
            }
        }
    }

    // 배치 전체를 병렬로 발행한다 - 건별로 순차 .get()을 기다리면 브로커가 느려질 때 한 건당 최대 SEND_TIMEOUT_SECONDS만큼씩
    // 누적 지연되므로, 모든 건을 동시에 보내고 한 번만 기다려 배치 전체의 지연 상한을 SEND_TIMEOUT_SECONDS 한 번으로 건다.
    // 조회/DB 반영 중 인프라성 예외(DB 커넥션 등)가 나면 전체를 잡아 로그만 남기고 다음 라운드/주기를 기약한다
    private int relayOnce() {
        try {
            List<PaymentEventOutbox> pending =
                    outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(
                            PaymentEventOutbox.MAX_RETRY_COUNT, BATCH_LIMIT);
            if (pending.isEmpty()) {
                return 0;
            }

            List<CompletableFuture<DispatchOutcome>> futures = pending.stream().map(this::sendAsync).toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<DispatchOutcome> outcomes = futures.stream().map(CompletableFuture::join).toList();

            List<PaymentEventOutbox> succeeded = outcomes.stream()
                    .filter(outcome -> outcome.error() == null)
                    .map(DispatchOutcome::event)
                    .toList();
            List<PaymentOutboxDispatcher.DispatchFailure> failed = outcomes.stream()
                    .filter(outcome -> outcome.error() != null)
                    .map(outcome -> new PaymentOutboxDispatcher.DispatchFailure(outcome.event(), outcome.error()))
                    .toList();

            dispatcher.markPublished(succeeded);
            dispatcher.recordFailures(failed);
            // 이번 라운드에서 단 한 건도 성공하지 못했다면(브로커 다운 등) 다음 라운드로 이어가지 않고 이번 틱을 종료한다 -
            // 계속 이어가면 3초 주기로 최대 5번(최소 15초) 재시도하도록 설계된 예산을 단일 틱 안에서 다 태워버린다.
            // 일부만 실패한 경우(브로커는 정상, 개별 건 문제)는 그대로 이어간다
            if (succeeded.isEmpty()) {
                return 0;
            }
            return pending.size();
        } catch (Exception e) {
            log.error("결제 아웃박스 릴레이 작업 중 예외 발생", e);
            return 0;
        }
    }

    // 같은 공동구매의 이벤트가 같은 파티션으로 모이도록 groupBuyId를 Kafka 메시지 키로 사용한다.
    // orTimeout으로 브로커 무응답 시에도 SEND_TIMEOUT_SECONDS 후엔 실패로 확정되고, handle로 성공/실패 모두
    // 예외 없이 DispatchOutcome으로 감싸 반환하므로 allOf가 개별 실패로 중단되지 않고 배치 전체를 계속 기다린다
    private CompletableFuture<DispatchOutcome> sendAsync(PaymentEventOutbox event) {
        return kafkaTemplate.send(TOPIC, String.valueOf(event.getGroupBuyId()), event.getPayload())
                .orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((result, error) -> new DispatchOutcome(event, error));
    }

    private record DispatchOutcome(PaymentEventOutbox event, Throwable error) {
    }
}
