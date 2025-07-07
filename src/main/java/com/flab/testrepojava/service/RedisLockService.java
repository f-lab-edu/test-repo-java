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

    public <T> T executeWithLock(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> logic) {
        metricsCollector.incrementAcquireAttempt(key);
        RLock lock = redissonClient.getLock(key);
        boolean acquired = false;
        long start = System.currentTimeMillis();

        try {
            acquired = lock.tryLock(waitTime, leaseTime, unit);
            if (!acquired) {
                metricsCollector.incrementFail(key);
                throw new IllegalStateException("락 획득 실패");
            }

            metricsCollector.incrementSuccess(key);
            return logic.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 대기 중 인터럽트 발생", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                long duration = System.currentTimeMillis() - start;
                metricsCollector.recordLockDuration(key, duration);
            }
        }
    }
}

