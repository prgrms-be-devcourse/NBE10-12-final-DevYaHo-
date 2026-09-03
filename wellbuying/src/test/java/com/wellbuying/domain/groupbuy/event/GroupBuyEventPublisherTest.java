package com.wellbuying.domain.groupbuy.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wellbuying.domain.address.repository.BuyerAddressRepository;
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
        BuyerAddressRepository buyerAddressRepository = org.mockito.Mockito.mock(BuyerAddressRepository.class);
        GroupBuyEventPublisher publisher = new GroupBuyEventPublisher(outboxRepository, buyerAddressRepository,
                new ObjectMapper());
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

    // 확정 참여자가 N명이면 참여자 단위로 이벤트를 N건 기록하되, save()를 N번 개별 호출하는 대신
    // saveAll()로 한 번에 묶어 저장하는지 검증 (참여자 수만큼 개별 INSERT 왕복이 나가지 않도록)
    @Test
    void publishCompleted은_확정_참여자_수만큼_이벤트를_saveAll로_한_번에_저장한다() {
        GroupBuyEventOutboxRepository outboxRepository = org.mockito.Mockito.mock(GroupBuyEventOutboxRepository.class);
        BuyerAddressRepository buyerAddressRepository = org.mockito.Mockito.mock(BuyerAddressRepository.class);
        GroupBuyEventPublisher publisher = new GroupBuyEventPublisher(outboxRepository, buyerAddressRepository,
                new ObjectMapper());
        GroupBuy groupBuy = groupBuyWithId(1L);
        GroupBuyPart part1 = GroupBuyPart.confirm(1L, 100L, 5);
        part1.applyFinalPrice(12_000);
        GroupBuyPart part2 = GroupBuyPart.confirm(1L, 200L, 3);
        part2.applyFinalPrice(12_000);

        publisher.publishCompleted(groupBuy, java.util.List.of(part1, part2));

        ArgumentCaptor<java.util.List<GroupBuyEventOutbox>> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(outboxRepository, times(1)).saveAll(captor.capture());
        verify(outboxRepository, org.mockito.Mockito.never()).save(any());
        assertThat(captor.getValue()).hasSize(2);
    }

    private GroupBuy groupBuyWithId(Long id) {
        GroupBuy groupBuy = GroupBuy.create(10L, 1L, "제목", LocalDateTime.now(), LocalDateTime.now().plusDays(1), 1, 100);
        org.springframework.test.util.ReflectionTestUtils.setField(groupBuy, "id", id);
        return groupBuy;
    }
}
