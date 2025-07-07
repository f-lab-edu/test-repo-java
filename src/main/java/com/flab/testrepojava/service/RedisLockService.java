package com.flab.testrepojava.service;

import com.flab.testrepojava.metrics.RedisLockMetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private final RedissonClient redissonClient;
    private final RedisLockMetricsCollector metricsCollector;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MILLIS = 100;

    public <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> logic) {
        metricsCollector.incrementAcquireAttempt(key);
        RLock lock = redissonClient.getLock(key);

        int attempts = 0;
        boolean acquired = false;
        long start = System.currentTimeMillis();

        while (attempts < MAX_RETRIES) {
            try {
                acquired = lock.tryLock(waitTime, leaseTime, unit);
                if (acquired) {
                    metricsCollector.incrementSuccess(key);
                    return logic.get();
                }

                attempts++;
                log.warn("[RedisLock] Lock attempt {} failed for key: {}. Retrying...", attempts, key);
                Thread.sleep(RETRY_BACKOFF_MILLIS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("락 시도 중 인터럽트 발생", e);
            }
        }

        metricsCollector.incrementFail(key);
        throw new IllegalStateException("Redis 락 획득 실패 (재시도 " + MAX_RETRIES + "회)");
    }

    public void releaseLock(RLock lock, String key, long startTimeMillis) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                long duration = System.currentTimeMillis() - startTimeMillis;
                metricsCollector.recordLockDuration(key, duration);
            }
        } catch (Exception e) {
            log.error("[RedisLock] 락 해제 중 예외 발생 - key: {}", key, e);
        }
    }
}

