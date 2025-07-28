package com.flab.testrepojava.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class DeadLetterFailureLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventId;
    private String topic;
    private String payload;
    private String reason;
    private int retryCount;
    private LocalDateTime failedAt;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.failedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }
}

