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
            } catch (IOException e) {
                // 이미 끊긴 연결에 쓰다 실패한 것 - onError/onCompletion 콜백이 정리해주므로 여기선 로그만 남긴다
                log.debug("SSE 전송 실패 - 연결이 이미 끊긴 것으로 보임. memberId: {}", event.memberId());
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
            } catch (IOException e) {
                // 정상 종료가 아니라 실패이므로 complete()가 아니라 completeWithError()로 onError 콜백을
                // 확실히 태워 repository에서 제거되도록 한다
                emitter.completeWithError(e);
            }
        }
    }
}
