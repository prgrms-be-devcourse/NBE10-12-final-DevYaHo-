package com.wellbuying.domain.groupbuy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.domain.groupbuy.entity.GroupBuy;
import com.wellbuying.domain.member.entity.Member;
import com.wellbuying.domain.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// @DataJpaTest(트랜잭션 롤백 기반)로는 여러 스레드가 진짜로 동시에 커밋하는 상황을 재현할 수 없어,
// 트랜잭션 경계 없이 실제 커밋이 일어나는 @SpringBootTest로 검증한다
class GroupBuyRepositoryConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private GroupBuyRepository groupBuyRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // increaseQuantity()가 "자바에서 읽은 값 + delta를 그대로 덮어쓰는" 방식이 아니라 DB에서
    // current_quantity = current_quantity + delta 를 원자적으로 수행하므로, 여러 트랜잭션이 동시에
    // 증가시켜도 lost update 없이 정확히 합산되는지 실제 동시성 상황으로 검증한다
    //
    // 실제 서비스(GroupBuyParticipationService)에서는 @Transactional 메서드 안에서 호출되므로 트랜잭션이
    // 이미 있지만, 이 테스트에서는 각 스레드가 직접 repository를 호출하므로 TransactionTemplate으로
    // 스레드마다 별도 트랜잭션을 명시적으로 열어준다 (@Modifying 쿼리는 트랜잭션 없이는 실행할 수 없다)
    @Test
    void 동시에_여러_번_증가시켜도_유실_없이_정확히_합산된다() throws InterruptedException {
        Member producer = memberRepository.save(
                Member.signUp("concurrency-" + System.nanoTime() + "@example.com", "encoded-password", "생산자"));
        GroupBuy groupBuy = groupBuyRepository.save(GroupBuy.create(1L, producer.getId(), "제목",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), 1, 1_000));
        Long groupBuyId = groupBuy.getId();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    transactionTemplate.executeWithoutResult(status -> groupBuyRepository.increaseQuantity(groupBuyId, 1));
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        try {
            if (!errors.isEmpty()) {
                errors.get(0).printStackTrace();
                throw new AssertionError(errors.size() + " threads failed, first cause: " + errors.get(0),
                        errors.get(0));
            }

            GroupBuy updated = groupBuyRepository.findById(groupBuyId).orElseThrow();
            assertThat(updated.getCurrentQuantity()).isEqualTo(threadCount);
        } finally {
            // 이 테스트는 @Transactional로 롤백되지 않는 @SpringBootTest라, 남겨두면 실행 중인 애플리케이션의
            // GroupBuyLifecycleScheduler가 이 행(startAt이 과거)을 집어서 ONGOING으로 전환시켜 버려
            // 다른 리포지토리 테스트의 상태별 조회 결과를 오염시킨다 - 반드시 직접 정리한다
            groupBuyRepository.deleteById(groupBuyId);
            memberRepository.deleteById(producer.getId());
        }
    }
}
