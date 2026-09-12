package com.wellbuying.domain.notification.sse;

import com.wellbuying.domain.notification.event.NotificationCreatedEvent;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 단일 서버(JVM) 인메모리 SSE 푸시 - Kafka Consumer가 알림을 저장(NotificationService)한 직후
// 발행하는 NotificationCreatedEvent를 받아, 같은 JVM 메모리에 붙어있는 emitter로 바로 흘려보낸다.
// 서버가 2대 이상으로 늘어나 "이벤트를 처리한 서버 != 유저가 붙어있는 서버" 문제가 실제로 발생하면
// 그때 Redis Pub/Sub 등으로 서버 간 라우팅을 추가한다 - 지금은 YAGNI.
@Service
public class NotificationSseService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseService.class);

    // 타임아웃을 무한대로 두면 죽은 연결이 영영 안 걸러질 수 있어 유한값을 두고, 프런트가 재연결한다
    private static final long TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final String EVENT_NOTIFICATION = "notification";

    private final SseEmitterRepository repository;

    public NotificationSseService(SseEmitterRepository repository) {
        this.repository = repository;
    }

    public SseEmitter subscribe(Long memberId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitter.onCompletion(() -> repository.remove(memberId, emitter));
        emitter.onTimeout(() -> repository.remove(memberId, emitter));
        emitter.onError(throwable -> repository.remove(memberId, emitter));
        repository.add(memberId, emitter);

        try {
            // 연결 직후 더미 이벤트를 하나 보내 프록시/브라우저가 응답을 버퍼링하지 않고 즉시 스트림을 열게 한다
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            repository.remove(memberId, emitter);
        }
        return emitter;
    }

    // AFTER_COMMIT 콜백은 기본적으로 커밋한 스레드(Kafka 컨슈머 스레드)에서 동기 실행되므로, emitter.send()가
    // 느려지면 그 스레드가 묶여 다음 메시지 처리가 밀릴 수 있다 - 전용 스레드풀로 넘겨 호출자 스레드를 막지 않는다
    @Async("notificationSseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        for (SseEmitter emitter : repository.findByMemberId(event.memberId())) {
            try {
                emitter.send(SseEmitter.event()
                        .name(EVENT_NOTIFICATION)
                        .data(event.notification(), MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                // IOException(끊긴 연결에 쓰기 실패) 외에도, 이미 완료/타임아웃된 emitter에 send()를 호출하면
                // IllegalStateException이 던져질 수 있다 - 좁게 잡으면 그 순간 루프가 멈춰서 같은 유저의
                // 나머지 emitter(다른 탭)에는 알림이 전달되지 않으므로 넓게 잡아 다음 emitter로 계속 진행한다.
                // 다음 하트비트 주기(최대 20초)까지 기다리지 않고 실패 시점에 바로 completeWithError로
                // onError 콜백을 태워 repository에서 제거한다
                log.debug("SSE 전송 실패로 인한 emitter 정리 - memberId: {}, cause: {}", event.memberId(), e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }

    // 알림이 한동안 없으면 연결이 idle 상태가 되어 프록시/브라우저가 끊을 수 있으므로 주기적으로
    // 빈 코멘트를 흘려보내 연결을 살아있게 유지하고, 죽은 연결은 여기서 걸러낸다
    @Scheduled(fixedRate = 20_000)
    public void sendHeartbeat() {
        for (SseEmitter emitter : repository.findAll()) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                // 좁게 IOException만 잡으면, 이미 완료/타임아웃된 emitter에서 던지는 IllegalStateException 같은
                // 런타임 예외가 스케줄러 스레드까지 새어나가 이번 주기의 나머지 emitter는 하트비트도 못 받고
                // 정리(completeWithError)도 안 된 채 남는다 - 넓게 잡아 다음 emitter로 계속 진행한다
                log.debug("하트비트 전송 실패로 인한 emitter 정리 - cause: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }
}
