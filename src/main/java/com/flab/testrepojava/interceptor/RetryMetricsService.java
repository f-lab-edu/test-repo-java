package com.flab.testrepojava.interceptor;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RetryMetricsService {

    private final MeterRegistry meterRegistry;

    public RetryMetricsService(MeterRegistry registry) {
        this.meterRegistry = registry;
    }

    public void countRetry(Exception e, Long productId) {
        log.info("🔁 [RETRY_METRIC] 재시도 카운트 기록 - 상품 ID: {}, 예외 타입: {}", productId, e.getClass().getSimpleName());
        if (e instanceof ObjectOptimisticLockingFailureException || e instanceof IllegalStateException) {
            meterRegistry.counter("product.retry.count",
                    "exception", e.getClass().getSimpleName(),
                    "productId", String.valueOf(productId)
            ).increment();
        }
    }
}
