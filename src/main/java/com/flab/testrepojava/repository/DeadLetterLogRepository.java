package com.flab.testrepojava.repository;

import com.flab.testrepojava.domain.DeadLetterLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeadLetterLogRepository extends JpaRepository<DeadLetterLog, Long> {
    long countByTopic(String topic);

    Optional<DeadLetterLog> findTopByPayloadOrderByCreatedAtDesc(String payload);

    Optional<DeadLetterLog> findTopByEventId(String eventId);
}
