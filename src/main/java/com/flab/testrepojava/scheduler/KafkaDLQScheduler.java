package com.flab.testrepojava.scheduler;

import com.flab.testrepojava.service.KafkaDLQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaDLQScheduler {

    private final KafkaDLQService kafkaDLQService;

    @Scheduled(fixedRateString = "${kafka.dlq.retry.interval.ms}")
    public void retryDLQPeriodically() {
        kafkaDLQService.retryAllDLQMessages(); // 여기선 void 사용
    }
}

