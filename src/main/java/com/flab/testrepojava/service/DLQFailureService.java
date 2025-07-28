package com.flab.testrepojava.service;

import com.flab.testrepojava.domain.DeadLetterFailureLog;
import com.flab.testrepojava.domain.DeadLetterSuccessLog;
import com.flab.testrepojava.repository.DeadLetterFailureLogRepository;
import com.flab.testrepojava.repository.DeadLetterSuccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DLQFailureService {

    private final DeadLetterFailureLogRepository deadLetterfailureLogRepository;
    private final DeadLetterSuccessLogRepository deadLetterSuccessLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;


    public List<DeadLetterFailureLog> getAllFailures() {
        return deadLetterfailureLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public String retryMessageById(Long id) {
        DeadLetterFailureLog failureLog = deadLetterfailureLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 실패 메시지입니다."));

        try {
            kafkaTemplate.send("stock-decrease", failureLog.getPayload()).get();

            // 2. 성공 로그에 저장
            deadLetterSuccessLogRepository.save(
                    DeadLetterSuccessLog.builder()
                            .eventId(failureLog.getEventId())
                            .payload(failureLog.getPayload())
                            .topic(failureLog.getTopic())
                            .successAt(LocalDateTime.now())
                            .build()
            );

            // 성공했으면 로그 삭제
            deadLetterfailureLogRepository.delete(failureLog);

            return "메시지 재처리 성공 (ID: " + id + ")";
        } catch (Exception e) {
            log.error("메시지 재처리 실패 (ID: {})", id, e);
            return "메시지 재처리 실패: " + e.getMessage();
        }
    }
}
