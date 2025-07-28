package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.DeadLetterFailureLog;
import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.domain.DeadLetterSuccessLog;
import com.flab.testrepojava.metrics.DLQMetricsService;
import com.flab.testrepojava.repository.DeadLetterFailureLogRepository;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import com.flab.testrepojava.repository.DeadLetterSuccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaDLQService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DeadLetterLogRepository deadLetterLogRepository;
    private final DeadLetterSuccessLogRepository deadLetterSuccessLogRepository;
    private final DeadLetterFailureLogRepository deadLetterFailureLogRepository;
    private final DLQMetricsService dlqMetricsService;


    public ResponseEntity<String> retrySingleMessage(String message) {
        try {
            kafkaTemplate.send("stock-decrease", message).get(); // 동기 전송
            return ResponseEntity.ok("DLQ 메시지를 재전송했습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("재전송 실패: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }


    @Transactional
    public DLQRetryResult retryAllDLQMessagesInternal() {
        List<DeadLetterLog> messages = deadLetterLogRepository.findAll();

        int successCount = 0;
        int failureCount = 0;

        for (DeadLetterLog entry : messages) {
            // 1. 최대 재시도 초과 시 → 실패 테이블로 이동
            if (entry.getRetryCount() >= 5) {
                deadLetterFailureLogRepository.save(
                        DeadLetterFailureLog.builder()
                                .topic(entry.getTopic())
                                .payload(entry.getPayload())
                                .reason("최대 재시도 초과")
                                .retryCount(entry.getRetryCount())
                                .build()
                );
                deadLetterLogRepository.delete(entry);
                log.warn("최대 재시도 초과로 수동 확인 DB로 이동: {}", entry.getPayload());
                continue;
            }

            // 2. exponential backoff 체크
            long secondsSinceLastTry = Duration.between(entry.getLastRetryAt(), LocalDateTime.now()).getSeconds();
            long requiredDelay = (long) Math.pow(2, entry.getRetryCount());

            if (secondsSinceLastTry < requiredDelay) {
                log.debug("아직 backoff 시간 도달 안됨 - 대기중: {}초 / 필요: {}초", secondsSinceLastTry, requiredDelay);
                continue;
            }

            // 3. 재시도 실행
            try {
                kafkaTemplate.send("stock-decrease", entry.getPayload()).get();
                dlqMetricsService.incrementSuccess();

                deadLetterSuccessLogRepository.save(
                        DeadLetterSuccessLog.builder()
                                .topic(entry.getTopic())
                                .payload(entry.getPayload())
                                .successAt(LocalDateTime.now())
                                .build()
                );

                deadLetterLogRepository.delete(entry);
                successCount++;

                try {
                    Thread.sleep(1000); // Slack rate limit 대응
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("DLQ 재처리 중 sleep 인터럽트 발생", ie);
                }

            } catch (Exception e) {
                log.error("DLQ 재처리 실패: {}", entry.getPayload(), e);
                failureCount++;
                dlqMetricsService.incrementFailure();

                entry.setRetryCount(entry.getRetryCount() + 1);
                entry.setLastRetryAt(LocalDateTime.now());
                deadLetterLogRepository.save(entry);
            }
        }

        return new DLQRetryResult(successCount, failureCount);
    }

    // @Scheduled 전용
    public void retryAllDLQMessages() {
        DLQRetryResult result = retryAllDLQMessagesInternal();
        log.info("DLQ 자동 재처리 완료 - 성공: {}, 실패: {}", result.successCount(), result.failureCount());
    }

    // 컨트롤러 호출용
    public String retryAllDLQMessagesWithResult() {
        DLQRetryResult result = retryAllDLQMessagesInternal();
        return "DLQ 전체 재처리 완료 - 성공: " + result.successCount() + "개, 실패: " + result.failureCount() + "개";
    }

    // Record class (Java 16+)
    public record DLQRetryResult(int successCount, int failureCount) {}

}
