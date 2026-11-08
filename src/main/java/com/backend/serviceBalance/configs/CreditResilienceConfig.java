package com.backend.serviceBalance.configs;


import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CreditResilienceConfig {

    @Bean
    public CircuitBreaker creditCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom().slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED).slidingWindowSize(20).minimumNumberOfCalls(10).failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(20)).permittedNumberOfCallsInHalfOpenState(3).recordException(CreditRetryPolicy::isRetryable).ignoreException(error -> !CreditRetryPolicy.isRetryable(error)).build();

        return CircuitBreaker.of("creditDatabase", config);
    }
}
