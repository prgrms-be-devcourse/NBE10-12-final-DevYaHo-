package com.wellbuying.groupbuy.event;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// 아웃박스 테이블 없이 Kafka로 바로 발행하되, 트랜잭션이 실제로 커밋된 뒤에만 발행되도록 보장하는 최소한의 안전장치
public final class AfterCommitExecutor {

    private AfterCommitExecutor() {
    }

    public static void run(Runnable callback) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    callback.run();
                }
            });
        } else {
            callback.run();
        }
    }
}
