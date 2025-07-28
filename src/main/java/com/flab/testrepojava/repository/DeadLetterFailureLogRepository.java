package com.flab.testrepojava.repository;

import com.flab.testrepojava.domain.DeadLetterFailureLog;
import com.flab.testrepojava.domain.DeadLetterLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeadLetterFailureLogRepository extends JpaRepository<DeadLetterFailureLog, Long> {
    List<DeadLetterFailureLog> findAllByTopic(String topic);

}
