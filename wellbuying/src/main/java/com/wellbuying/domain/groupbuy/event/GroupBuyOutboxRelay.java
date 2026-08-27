package com.wellbuying.domain.groupbuy.event;

import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 아웃박스에 쌓인 미발행 이벤트를 주기적으로 폴링해 Kafka로 발행한다.
// 상태 변경(성사/실패/취소 확정)과 아웃박스 기록은 GroupBuyEventPublisher가 이미 같은 트랜잭션으로 묶어뒀으므로,
// 여기서 Kafka 발행이 잠시 실패하거나 이 프로세스가 죽어도 이벤트 자체는 DB에 남아 다음 주기에 재시도된다
@Component
public class GroupBuyOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(GroupBuyOutboxRelay.class);

    private static final String TOPIC = "groupbuy-events";
    private static final long SEND_TIMEOUT_SECONDS = 5;

    // 한 번의 실행에서 처리할 최대 건수 - GroupBuyLifecycleScheduler의 BATCH_LIMIT과 동일한 이유(예측 가능한 메모리 사용,
    // 처리 못한 나머지는 published_at이 그대로 null이라 다음 실행에서 자연스럽게 이어서 처리됨)
    private static final Limit BATCH_LIMIT = Limit.of(200);

    private final GroupBuyEventOutboxRepository outboxRepository;
    private final GroupBuyOutboxDispatcher dispatcher;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public GroupBuyOutboxRelay(GroupBuyEventOutboxRepository outboxRepository, GroupBuyOutboxDispatcher dispatcher,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.dispatcher = dispatcher;
        this.kafkaTemplate = kafkaTemplate;
    }

    // 배치 전체를 병렬로 발행한다 - 건별로 순차 .get()을 기다리면 브로커가 느려질 때 한 건당 최대 SEND_TIMEOUT_SECONDS만큼씩
    // 누적 지연되어(최악의 경우 배치 크기 x SEND_TIMEOUT_SECONDS) 이 스케줄러 스레드가 오래 묶인다. 모든 건을 동시에
    // 보내고 한 번만 기다리면 배치 전체의 지연이 SEND_TIMEOUT_SECONDS 한 번으로 상한이 걸린다
    // 조회/DB 반영(markPublished, recordFailures) 중 예외가 나면(DB 커넥션 문제 등) 전체를 잡아 로그만 남기고
    // 다음 3초 주기를 기약한다 - Kafka 발행 결과(성공/실패)는 sendAsync의 handle()에서 이미 예외 없이
    // DispatchOutcome으로 감싸두었으므로 여기서 잡을 예외는 그 바깥의 인프라성 문제뿐이다
    @Scheduled(fixedDelay = 3_000)
    public void relay() {
        try {
            List<GroupBuyEventOutbox> pending =
                    outboxRepository.findByPublishedAtIsNullAndRetryCountLessThanOrderByIdAsc(
                            GroupBuyEventOutbox.MAX_RETRY_COUNT, BATCH_LIMIT);
            if (pending.isEmpty()) {
                return;
            }

            List<CompletableFuture<DispatchOutcome>> futures = pending.stream().map(this::sendAsync).toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            List<DispatchOutcome> outcomes = futures.stream().map(CompletableFuture::join).toList();

            List<GroupBuyEventOutbox> succeeded = outcomes.stream()
                    .filter(outcome -> outcome.error() == null)
                    .map(DispatchOutcome::event)
                    .toList();
            List<GroupBuyOutboxDispatcher.DispatchFailure> failed = outcomes.stream()
                    .filter(outcome -> outcome.error() != null)
                    .map(outcome -> new GroupBuyOutboxDispatcher.DispatchFailure(outcome.event(), outcome.error()))
                    .toList();

            dispatcher.markPublished(succeeded);
            dispatcher.recordFailures(failed);
        } catch (Exception e) {
            log.error("아웃박스 릴레이 작업 중 예외 발생", e);
        }
    }

    // 같은 공동구매의 이벤트가 같은 파티션으로 모이도록 groupBuyId를 Kafka 메시지 키로 사용한다.
    // orTimeout으로 브로커 무응답 시에도 SEND_TIMEOUT_SECONDS 후엔 실패로 확정되고, handle로 성공/실패 모두
    // 예외 없이 DispatchOutcome으로 감싸 반환하므로 allOf가 개별 실패로 중단되지 않고 배치 전체를 계속 기다린다
    private CompletableFuture<DispatchOutcome> sendAsync(GroupBuyEventOutbox event) {
        return kafkaTemplate.send(TOPIC, String.valueOf(event.getGroupBuyId()), event.getPayload())
                .orTimeout(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((result, error) -> new DispatchOutcome(event, error));
    }

    private record DispatchOutcome(GroupBuyEventOutbox event, Throwable error) {
    }
}
