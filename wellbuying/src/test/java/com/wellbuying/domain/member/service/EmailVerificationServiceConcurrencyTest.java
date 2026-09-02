package com.wellbuying.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wellbuying.AbstractIntegrationTest;
import com.wellbuying.global.exception.BusinessException;
import com.wellbuying.global.exception.ErrorCode;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

// EmailCooldownGuard.acquire()(SETNX)가 동시 요청에서도 정확히 1건만 통과시키는지 실제 Redis로 검증한다.
// Mockito 목 객체로는 진짜 동시 접근을 재현할 수 없어, 로컬에 떠 있는 실제 Redis를 사용하는 @SpringBootTest로 확인한다.
class EmailVerificationServiceConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void 동시에_10개_요청이_와도_쿨다운_선점은_1건만_성공한다() throws InterruptedException {
        String email = "concurrency-" + System.nanoTime() + "@example.com";
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger cooldownExceptionCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    emailVerificationService.sendVerificationCode(email);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.EMAIL_VERIFICATION_COOLDOWN) {
                        cooldownExceptionCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        try {
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(cooldownExceptionCount.get()).isEqualTo(threadCount - 1);
        } finally {
            redisTemplate.delete("email:verification:" + email);
            redisTemplate.delete("email:cooldown:verification:" + email);
        }
    }
}
