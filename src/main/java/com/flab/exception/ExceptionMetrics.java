package com.flab.exception;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ExceptionMetrics {

    private final Counter http500Counter;

    public ExceptionMetrics(MeterRegistry registry) {
        this.http500Counter = Counter.builder("custom_http_500_errors")
                .description("Total number of 500 Internal Server Errors")
                .register(registry);
    }

    public void incrementHttp500() {
        http500Counter.increment();
    }
}
