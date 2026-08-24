package com.wellbuying.groupbuy.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Lua 스크립트(participate_groupbuy.lua) 기반 원자적 카운터의 동시성 정확성을 실제 Redis로 검증
@SpringBootTest
class GroupBuyCounterRepositoryTest {

    @Autowired
    private GroupBuyCounterRepository groupBuyCounterRepository;

    private Long groupBuyId;

    private Long newGroupBuyId() {
        groupBuyId = System.nanoTime();
        return groupBuyId;
    }

    @AfterEach
    void cleanUp() {
        if (groupBuyId != null) {
            groupBuyCounterRepository.delete(groupBuyId);
        }
    }

    // 초기화 후 첫 참여는 요청 수량만큼 누적되어 반환되는지 검증
    @Test
    void 초기화_후_첫_참여는_요청_수량을_누적한다() {
        Long id = newGroupBuyId();
        groupBuyCounterRepository.initialize(id, Duration.ofMinutes(5));

        long result = groupBuyCounterRepository.tryIncrease(id, 30, 100);

        assertThat(result).isEqualTo(30);
    }

    // 여러 번 참여하면 누적 수량이 계속 더해지는지 검증
    @Test
    void 연속으로_참여하면_수량이_누적된다() {
        Long id = newGroupBuyId();
        groupBuyCounterRepository.initialize(id, Duration.ofMinutes(5));

        groupBuyCounterRepository.tryIncrease(id, 30, 100);
        long result = groupBuyCounterRepository.tryIncrease(id, 40, 100);

        assertThat(result).isEqualTo(70);
    }

    // 최대 수량을 초과하는 참여 요청은 -1을 반환하고 카운터를 증가시키지 않는지 검증
    @Test
    void 최대_수량을_초과하면_증가하지_않고_실패를_반환한다() {
        Long id = newGroupBuyId();
        groupBuyCounterRepository.initialize(id, Duration.ofMinutes(5));
        groupBuyCounterRepository.tryIncrease(id, 90, 100);

        long result = groupBuyCounterRepository.tryIncrease(id, 20, 100);

        assertThat(result).isEqualTo(-1);
        assertThat(groupBuyCounterRepository.tryIncrease(id, 10, 100)).isEqualTo(100);
    }

    // deleteAll()이 여러 공동구매의 카운터를 한 번의 호출로 모두 정리하는지 검증
    // (GroupBuyLifecycleScheduler가 배치 마감 처리 시 건별 delete 대신 사용하는 메서드)
    @Test
    void deleteAll은_여러_카운터를_한_번에_정리한다() {
        Long id1 = System.nanoTime();
        Long id2 = id1 + 1;
        groupBuyCounterRepository.initialize(id1, Duration.ofMinutes(5));
        groupBuyCounterRepository.initialize(id2, Duration.ofMinutes(5));
        groupBuyCounterRepository.tryIncrease(id1, 30, 100);
        groupBuyCounterRepository.tryIncrease(id2, 40, 100);

        groupBuyCounterRepository.deleteAll(List.of(id1, id2));

        assertThat(groupBuyCounterRepository.tryIncrease(id1, 0, 100)).isEqualTo(0);
        assertThat(groupBuyCounterRepository.tryIncrease(id2, 0, 100)).isEqualTo(0);
    }

    // decrease()가 취소된 수량만큼 카운터를 원복하는지 검증
    @Test
    void decrease는_카운터를_원복한다() {
        Long id = newGroupBuyId();
        groupBuyCounterRepository.initialize(id, Duration.ofMinutes(5));
        groupBuyCounterRepository.tryIncrease(id, 50, 100);

        groupBuyCounterRepository.decrease(id, 20);

        assertThat(groupBuyCounterRepository.tryIncrease(id, 0, 100)).isEqualTo(30);
    }

    // 동시에 여러 참여 요청이 들어와도 Lua 스크립트의 원자성 덕분에 재고를 초과해서 증가시키지 않는지 검증
    @Test
    void 동시_참여_요청에서도_최대_수량을_초과하지_않는다() throws InterruptedException {
        Long id = newGroupBuyId();
        int maxQuantity = 100;
        groupBuyCounterRepository.initialize(id, Duration.ofMinutes(5));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    long result = groupBuyCounterRepository.tryIncrease(id, 10, maxQuantity);
                    if (result >= 0) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(maxQuantity / 10);
        assertThat(groupBuyCounterRepository.tryIncrease(id, 0, maxQuantity)).isEqualTo(maxQuantity);
    }
}
