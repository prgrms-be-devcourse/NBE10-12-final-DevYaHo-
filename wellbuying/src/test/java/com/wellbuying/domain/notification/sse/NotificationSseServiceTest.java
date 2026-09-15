package com.wellbuying.domain.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wellbuying.domain.notification.dto.NotificationResponse;
import com.wellbuying.domain.notification.entity.NotificationType;
import com.wellbuying.domain.notification.event.NotificationCreatedEvent;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseServiceTest {

    private final SseEmitterRepository repository = mock(SseEmitterRepository.class);
    private final NotificationSseService service = new NotificationSseService(repository);

    private NotificationResponse notification() {
        return new NotificationResponse(1L, NotificationType.GROUP_BUY_COMPLETED, 10L, 20L, "공동구매가 성사됐어요", false,
                LocalDateTime.now());
    }

    @Test
    @DisplayName("subscribe: 연결 직후 더미 이벤트 전송이 IOException으로 실패하면 repository에서 즉시 제거한다")
    void subscribe_연결_직후_전송_실패시_즉시_제거() throws IOException {
        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class,
                (mock, context) -> doThrow(new IOException("broken pipe"))
                        .when(mock).send(any(SseEmitter.SseEventBuilder.class)))) {

            SseEmitter result = service.subscribe(1L);

            SseEmitter constructed = mocked.constructed().get(0);
            assertThat(result).isSameAs(constructed);
            verify(repository).add(eq(1L), eq(constructed));
            verify(repository).remove(eq(1L), eq(constructed));
        }
    }

    @Test
    @DisplayName("subscribe: 더미 이벤트 전송이 성공하면 repository에 등록된 채로 유지된다")
    void subscribe_정상() {
        try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
            SseEmitter result = service.subscribe(1L);

            SseEmitter constructed = mocked.constructed().get(0);
            verify(repository).add(eq(1L), eq(constructed));
            verify(repository, never()).remove(any(), any());
            assertThat(result).isSameAs(constructed);
        }
    }

    @Test
    @DisplayName("onNotificationCreated: 여러 emitter 중 하나가 전송 중 예외를 던져도 나머지 emitter는 정상 전송되고, 실패한 emitter만 completeWithError로 정리된다")
    void onNotificationCreated_일부_emitter_실패해도_나머지는_정상_전송() throws IOException {
        SseEmitter failing = mock(SseEmitter.class);
        SseEmitter healthy = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(failing).send(any(SseEmitter.SseEventBuilder.class));
        NotificationCreatedEvent event = new NotificationCreatedEvent(1L, notification());
        when(repository.findByMemberId(1L)).thenReturn(List.of(failing, healthy));

        service.onNotificationCreated(event);

        verify(failing).completeWithError(any());
        verify(healthy).send(any(SseEmitter.SseEventBuilder.class));
        verify(healthy, never()).completeWithError(any());
    }

    @Test
    @DisplayName("sendHeartbeat: 한 emitter의 하트비트 전송이 실패해도 나머지 emitter는 영향받지 않고 하트비트를 받는다")
    void sendHeartbeat_일부_emitter_실패해도_나머지는_영향받지_않는다() throws IOException {
        SseEmitter failing = mock(SseEmitter.class);
        SseEmitter healthy = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(failing).send(any(SseEmitter.SseEventBuilder.class));
        when(repository.findAll()).thenReturn(List.of(failing, healthy));

        service.sendHeartbeat();

        verify(failing).completeWithError(any());
        verify(healthy).send(any(SseEmitter.SseEventBuilder.class));
        verify(healthy, never()).completeWithError(any());
    }
}
