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
}
