package com.wellbuying.domain.notification.sse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// 단일 서버(JVM) 전제의 인메모리 SSE 연결 저장소 - 같은 회원이 여러 탭/기기에서 접속할 수 있어
// memberId 하나에 여러 emitter를 매핑한다.
// 서버가 여러 대로 늘어나면(스케일아웃) "이벤트를 소비한 서버 != 유저가 붙어있는 서버" 문제가 생기므로
// 그 시점에 Redis Pub/Sub 등으로 라우팅을 교체해야 한다 - 지금은 YAGNI.
@Component
public class SseEmitterRepository {

    private final Map<Long, List<SseEmitter>> emittersByMemberId = new ConcurrentHashMap<>();

    // add/remove 모두 리스트 mutation을 compute 계열의 재매핑 함수 "안에서" 수행해야 한다.
    // computeIfAbsent(...).add(...)처럼 리스트를 반환받은 뒤 바깥에서 add하면, 그 사이에 다른
    // 스레드의 remove가 끼어들어 리스트를 맵에서 지워버리는 순간 이 add는 이미 버려진(orphan)
    // 리스트에 등록되는 레이스가 생긴다 - 이렇게 하면 같은 key에 대한 add/remove가 서로 배타적으로
    // 실행되어(ConcurrentHashMap이 bin 단위로 락을 잡음) 안전하다.
    public void add(Long memberId, SseEmitter emitter) {
        emittersByMemberId.compute(memberId, (key, emitters) -> {
            List<SseEmitter> result = emitters != null ? emitters : new CopyOnWriteArrayList<>();
            result.add(emitter);
            return result;
        });
    }

    // 리스트가 비면 키 자체를 맵에서 제거해 로그아웃/이탈한 유저의 빈 리스트가 쌓이는 걸 막는다
    public void remove(Long memberId, SseEmitter emitter) {
        emittersByMemberId.computeIfPresent(memberId, (key, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }

    public List<SseEmitter> findByMemberId(Long memberId) {
        return emittersByMemberId.getOrDefault(memberId, List.of());
    }

    public List<SseEmitter> findAll() {
        return emittersByMemberId.values().stream().flatMap(List::stream).toList();
    }
}
