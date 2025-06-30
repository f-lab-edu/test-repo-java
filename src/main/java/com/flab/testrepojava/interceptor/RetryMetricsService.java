package com.flab.testrepojava.interceptor;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class RetryMetricsService {

    private final MeterRegistry meterRegistry;

    public RetryMetricsService(MeterRegistry registry) {
        this.meterRegistry = registry;
    }

    public void countRetry(Exception e, Long productId) {
        if (e instanceof ObjectOptimisticLockingFailureException || e instanceof IllegalStateException) {
            meterRegistry.counter("product.retry.count",
                    "exception", e.getClass().getSimpleName(),
                    "productId", String.valueOf(productId)
            ).increment();
        }
    }
}
