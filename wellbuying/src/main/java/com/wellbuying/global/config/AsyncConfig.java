package com.wellbuying.global.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // 메일 발송 전용 스레드풀 - MailService.sendHtmlEmail의 @Async("mailExecutor")에서 사용
    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-");
        executor.initialize();
        return executor;
    }

    // 로그인 활동(lastLoginAt/휴면 전환) 갱신 전용 스레드풀 - MemberLoginEventListener.handleLoginEvent의 @Async("memberEventExecutor")에서 사용
    // CallerRunsPolicy: 큐+풀이 모두 찬 경우 이벤트를 버리는(AbortPolicy 기본값) 대신 호출 스레드(AFTER_COMMIT 콜백 스레드)에서 직접 실행해 유실 방지
    @Bean(name = "memberEventExecutor")
    public Executor memberEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("member-event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    // 공동구매 마감 배치(GroupBuyLifecycleScheduler.closeOngoingGroupBuys)의 건별 확정을 병렬로 실행하는 전용 스레드풀 -
    // 순차 for 루프로 돌리면 건당 DB 왕복 시간이 그대로 배치 전체에 곱해지므로, HikariCP 커넥션 풀(30개) 안에서
    // 다른 API 트래픽에도 커넥션을 남겨두도록 16개로 제한해 병렬화한다. 큐는 BATCH_LIMIT(500)을 한 번에 받아도
    // 거절되지 않도록 넉넉히 잡는다
    @Bean(name = "groupBuyLifecycleExecutor")
    public Executor groupBuyLifecycleExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("gb-lifecycle-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // 프로필 이미지 pending 태그 제거/이전 이미지 삭제 전용 스레드풀 - ProfileImageEventListener의 @Async("s3ConfirmExecutor")에서 사용
    // DiscardPolicy: 실패해도 재시도/보상 트랜잭션 없이 로그만 남기는 best-effort 정리 작업이라, 큐가 찬 경우 Tomcat 스레드가 S3를 동기 호출하게 만드는 CallerRunsPolicy보다
    // 작업을 버리는 편이 메인 API 응답 지연을 막는 데 낫다
    @Bean(name = "s3ConfirmExecutor")
    public Executor s3ConfirmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("s3-confirm-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
