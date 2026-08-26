package com.wellbuying.domain.groupbuy.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.groupbuy.entity.GroupBuyEventOutbox;
import com.wellbuying.domain.groupbuy.entity.GroupBuyPart;
import com.wellbuying.domain.groupbuy.repository.GroupBuyEventOutboxRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

// 아웃박스 패턴의 핵심 - Kafka로 직접 보내지 않고, 이 트랜잭션 안에서 아웃박스 행만 저장하는지 검증한다.
// (실제 Kafka 발행은 GroupBuyOutboxDispatcherTest가 다룬다)
class GroupBuyEventPublisherTest {

    @Test
    void publishCanceled은_이벤트타입과_직렬화된_페이로드를_담은_아웃박스_행을_한_건_저장한다() {
        GroupBuyEventOutboxRepository outboxRepository = org.mockito.Mockito.mock(GroupBuyEventOutboxRepository.class);
        GroupBuyEventPublisher publisher = new GroupBuyEventPublisher(outboxRepository, new ObjectMapper());
        GroupBuy groupBuy = groupBuyWithId(1L);

        publisher.publishCanceled(groupBuy);

        ArgumentCaptor<GroupBuyEventOutbox> captor = ArgumentCaptor.forClass(GroupBuyEventOutbox.class);
        verify(outboxRepository, times(1)).save(captor.capture());
        GroupBuyEventOutbox saved = captor.getValue();
        assertThat(saved.getGroupBuyId()).isEqualTo(1L);
        assertThat(saved.getEventType()).isEqualTo(GroupBuyEventType.GROUP_BUY_CANCELED.code());
        assertThat(saved.getPayload()).contains("\"groupBuyId\":1").contains(GroupBuyEventType.GROUP_BUY_CANCELED.code());
        assertThat(saved.getPublishedAt()).isNull();
    }

    // 확정 참여자가 N명이면 참여자 단위로 이벤트를 N건 개별 기록하는지 검증 (결제 도메인이 참여자별로 후속 처리를 하도록)
    @Test
    void publishCompleted은_확정_참여자_수만큼_아웃박스_행을_개별_저장한다() {
        GroupBuyEventOutboxRepository outboxRepository = org.mockito.Mockito.mock(GroupBuyEventOutboxRepository.class);
        GroupBuyEventPublisher publisher = new GroupBuyEventPublisher(outboxRepository, new ObjectMapper());
        GroupBuy groupBuy = groupBuyWithId(1L);
        GroupBuyPart part1 = GroupBuyPart.confirm(1L, 100L, 5);
        part1.applyFinalPrice(12_000);
        GroupBuyPart part2 = GroupBuyPart.confirm(1L, 200L, 3);
        part2.applyFinalPrice(12_000);

        publisher.publishCompleted(groupBuy, java.util.List.of(part1, part2));

        verify(outboxRepository, times(2)).save(any());
    }

    private GroupBuy groupBuyWithId(Long id) {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목", LocalDateTime.now(), LocalDateTime.now().plusDays(1), 1, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(groupBuy, "id", id);
        return groupBuy;
    }
}
