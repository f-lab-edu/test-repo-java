package com.flab.testrepojava.slack;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class SlackNotifier {

    private final WebClient slackWebClient;

    private static final int MAX_RETRIES = 3;
    private static final Duration BACKOFF_DURATION = Duration.ofSeconds(2);

    public void send(String message) {
        Map<String, String> payload = Map.of("text", message);

        slackWebClient.post()
                .uri(System.getenv("SLACK_WEBHOOK_URL"))  // 또는 @Value("${SLACK_WEBHOOK_URL}")
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatus.TOO_MANY_REQUESTS::equals, clientResponse -> {
                    log.warn("🚫 Slack 전송 제한(429 Too Many Requests)");
                    return Mono.error(new RuntimeException("Slack rate limited"));
                })
                .bodyToMono(Void.class)
                .retryWhen(
                        Retry.backoff(MAX_RETRIES, BACKOFF_DURATION)
                                .filter(throwable -> throwable instanceof WebClientResponseException.TooManyRequests)
                                .onRetryExhaustedThrow((retryBackoffSpec, signal) ->
                                        new RuntimeException("Slack 전송 재시도 실패"))
                )
                .doOnError(e -> log.error("🔥 Slack 전송 실패: {}", e.getMessage()))
                .subscribe(); // 비동기 호출 실행
    }


}

