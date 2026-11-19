package com.bbcc.kidly.gateway.ratelimit;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class GlobalRateLimitFilterTest {

    @Mock
    private SharedRequestRateLimiter limiter;

    @Mock
    private ProblemResponse problem;

    @Mock
    private GatewayFilterChain chain;

    @Test
    @DisplayName("client-enabled가 false면 Redis를 부르지 않고 요청을 넘긴다")
    void passesWithoutLimiterWhenDisabled() {
        var limit = new RateLimitProperties.Limit(1, Duration.ofHours(1));
        var properties = new RateLimitProperties(false, limit, limit, limit);
        var filter = new GlobalRateLimitFilter(limiter, properties, problem, new SimpleMeterRegistry());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/products/1"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(limiter);
    }
}
