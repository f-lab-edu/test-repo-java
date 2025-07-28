package com.flab.testrepojava.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flab.testrepojava.domain.DeadLetterFailureLog;
import com.flab.testrepojava.domain.DeadLetterLog;
import com.flab.testrepojava.domain.EventParticipationLog;
import com.flab.testrepojava.domain.Member;
import com.flab.testrepojava.dto.StockDecreaseEvent;
import com.flab.testrepojava.email.EmailService;
import com.flab.testrepojava.exception.DLQExceptionFilter;
import com.flab.testrepojava.repository.DeadLetterFailureLogRepository;
import com.flab.testrepojava.repository.DeadLetterLogRepository;
import com.flab.testrepojava.repository.EventParticipationLogRepository;
import com.flab.testrepojava.service.MemberService;
import com.flab.testrepojava.service.ProductService;
import com.flab.testrepojava.slack.SlackNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final SlackNotifier slackNotifier;
    private final ProductService productService;
    private final EmailService emailService;
    private final MemberService memberService;
    private final EventParticipationLogRepository eventParticipationLogRepository;
    private final DeadLetterFailureLogRepository deadLetterFailureLogRepository;
    private final DeadLetterLogRepository deadLetterLogRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "stock-decrease",
            groupId = "product-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message) {
        log.info("📩 Kafka 메시지 수신: {}", message);

        StockDecreaseEvent event;

        // 1. JSON 파싱
        try {
            event = objectMapper.readValue(message, StockDecreaseEvent.class);
        } catch (JsonProcessingException e) {
            String fallbackEventId = extractEventIdFromPayload(message); // 실패 시 fallback
            log.warn("Kafka 메시지 파싱 실패 - JSON 형식 아님: {}", message);
            saveToDLQ("stock-decrease", message, fallbackEventId, "JSON 파싱 오류: " + e.getMessage());
            return;
        }

        // 2. eventId 유효성 검사
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            log.warn("eventId 누락: {}", message);
            saveToDLQ("stock-decrease", message, null, "eventId 누락");
            return;
        }

        // 3. memberEmail 유효성 검사
        if (event.getMemberEmail() == null || event.getMemberEmail().isBlank()) {
            log.warn("memberEmail 누락: {}", message);
            saveToDLQ("stock-decrease", message, event.getEventId(), "memberEmail 누락");
            return;
        }

        try {
            // 4. 유저 조회
            Member member = memberService.findByEmailOrThrow(event.getMemberEmail());
            Long userId = member.getId();

            // 5. 재고 감소
            productService.decreaseQuantityWithPessimisticLock(event.getProductId(), 1);

            // 6. 이벤트 참여 로그 저장
            eventParticipationLogRepository.save(
                    EventParticipationLog.builder()
                            .productId(event.getProductId())
                            .userId(userId)
                            .status("SUCCESS")
                            .build()
            );

            // 7. 이메일 전송
            emailService.sendParticipationSuccess(userId, event.getProductId());

            // 8. 슬랙 알림 (실패해도 무시)
            try {
                slackNotifier.queueMessage(String.format(
                        "🎉 유저 %d가 상품 %d 이벤트에 성공했습니다!",
                        userId, event.getProductId()
                ));
            } catch (Exception slackEx) {
                log.warn("Slack 전송 실패 (무시됨): {}", slackEx.getMessage());
            }

            log.info("Kafka 메시지 처리 완료 - 유저 ID: {}, 상품 ID: {}", userId, event.getProductId());

        } catch (Exception e) {
            if (DLQExceptionFilter.isBusinessException(e)) {
                log.warn("비즈니스 예외 발생. 메시지 폐기: {}", message, e);
                return; // DLQ 저장 안 함
            }

            if (DLQExceptionFilter.isSystemException(e)) {
                log.error("시스템 예외 발생, DLQ 저장: {}", message, e);
                saveToDLQ("stock-decrease", message, event.getEventId(), "시스템 예외: " + e.getMessage());
                return;
            }

            log.error("처리 불가 예외 발생: {}", message, e);
        }
    }



    @KafkaListener(topics = "stock-decrease.DLT", groupId = "dlt-monitor-group")
    public void monitorDLQ(
            String failedMessage,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp
    ) {
        log.error("📦 DLQ 메시지 감지됨: {}", failedMessage);

        // eventId 추출 (실패해도 null 허용)
        String eventId = extractEventIdFromPayload(failedMessage);

        // DLQ 로그 저장 (중복 체크 + retryCount 증가 포함)
        saveToDLQ(topic, failedMessage, eventId, "Kafka 처리 실패 (DLQ 모니터링)");

        // DLQ 메시지 누적 개수 조회
        long dlqCount = deadLetterLogRepository.countByTopic(topic);

        try {
            // JSON 파싱/시스템 예외 등 주요 장애 조건 감지
            boolean isCriticalError = failedMessage.contains("JSON 파싱 오류") || failedMessage.contains("시스템 예외");

            // 테스트 전용 조건, 실무에서는 제거 가능
            boolean isTestFailure = failedMessage.contains("fail");

            // 조건에 따라 Slack 알림 발송
            if (isTestFailure || isCriticalError || dlqCount > 10) {
            //if (isCriticalError || dlqCount > 10) {
                String slackMessage = String.format(
                        "DLQ 메시지 감지됨!\n" +
                                "• 토픽: %s\n" +
                                "• 파티션: %d\n" +
                                "• 오프셋: %d\n" +
                                "• 시간: %s\n" +
                                "• 메시지: %s%s\n" +
                                "• 재처리 링크: /kafka/retry-dlt",
                        topic,
                        partition,
                        offset,
                        Instant.ofEpochMilli(timestamp),
                        failedMessage,
                        dlqCount > 10 ? "\n️• 누적 메시지 수: " + dlqCount + "개" : ""
                );

                slackNotifier.queueMessage(slackMessage);
            }

        } catch (Exception e) {
            log.warn("DLQ Slack 알림 실패 (무시됨): {}", e.getMessage());
        }
    }


    private void saveToDLQ(String topic, String payload, String eventId, String reason) {
        Optional<DeadLetterLog> existing = deadLetterLogRepository.findTopByEventId(eventId);

        if (existing.isPresent()) {
            DeadLetterLog logEntry = existing.get();

            if (logEntry.getRetryCount() + 1 >= 5) {
                deadLetterFailureLogRepository.save(
                        DeadLetterFailureLog.builder()
                                .eventId(eventId)
                                .topic(topic)
                                .payload(payload)
                                .reason("최대 재시도 초과")
                                .retryCount(logEntry.getRetryCount() + 1)
                                .build()
                );
                deadLetterLogRepository.delete(logEntry);
                log.warn("최대 재시도 초과로 failure log로 이동 (eventId: {}): {}", eventId, payload);
                return;
            }

            logEntry.setRetryCount(logEntry.getRetryCount() + 1);
            logEntry.setLastRetryAt(LocalDateTime.now());
            logEntry.setReason(reason);
            deadLetterLogRepository.save(logEntry);

        } else {
            deadLetterLogRepository.save(
                    DeadLetterLog.builder()
                            .eventId(eventId)
                            .topic(topic)
                            .payload(payload)
                            .reason(reason)
                            .retryCount(1)
                            .lastRetryAt(LocalDateTime.now())
                            .build()
            );
        }
    }
    private String extractEventIdFromPayload(String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            JsonNode eventIdNode = jsonNode.get("eventId");
            return eventIdNode != null ? eventIdNode.asText() : null;
        } catch (JsonProcessingException e) {
            log.warn("eventId 파싱 실패 (payload): {}", payload);
            return null;
        }
    }

}
