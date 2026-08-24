package com.wellbuying.groupbuy.event;

import com.wellbuying.groupbuy.domain.GroupBuy;
import com.wellbuying.groupbuy.domain.GroupBuyPart;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

// 아웃박스 패턴 없이 KafkaTemplate으로 직접 발행한다 (1단계 범위: 발행 실패/유실에 대한 재처리는 아직 없음)
@Component
public class GroupBuyEventPublisher {

    private static final String TOPIC = "groupbuy-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public GroupBuyEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // 공동구매 성사 - 확정된 참여자 각각에 대해 이벤트를 개별 발행 (결제 도메인이 참여자 단위로 후속 처리를 하도록)
    public void publishCompleted(GroupBuy groupBuy, List<GroupBuyPart> confirmedParts) {
        for (GroupBuyPart part : confirmedParts) {
            send(groupBuy.getId(), GroupBuyCompletedEvent.of(groupBuy, part));
        }
    }

    public void publishFailed(GroupBuy groupBuy) {
        send(groupBuy.getId(), GroupBuyFailedEvent.of(groupBuy));
    }

    public void publishCanceled(GroupBuy groupBuy) {
        send(groupBuy.getId(), GroupBuyCanceledEvent.of(groupBuy));
    }

    // 같은 공동구매의 이벤트가 같은 파티션으로 모이도록 groupBuyId를 메시지 키로 사용
    private void send(Long groupBuyId, Object event) {
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(TOPIC, String.valueOf(groupBuyId), payload);
    }
}
