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

    public void add(Long memberId, SseEmitter emitter) {
        emittersByMemberId.computeIfAbsent(memberId, key -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    // 빈 리스트가 남아도 메모리 부담이 미미하므로 맵에서 키 자체를 지우지는 않는다 - 동시에 새
    // emitter가 등록되는 순간과 겹쳐 리스트를 통째로 잃어버리는 레이스를 피하기 위함
    public void remove(Long memberId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByMemberId.get(memberId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    public List<SseEmitter> findByMemberId(Long memberId) {
        return emittersByMemberId.getOrDefault(memberId, List.of());
    }

    public List<SseEmitter> findAll() {
        return emittersByMemberId.values().stream().flatMap(List::stream).toList();
    }
}
