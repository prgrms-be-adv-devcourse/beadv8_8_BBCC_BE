package com.bbcc.kidly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RateLimitSettingsGatewayFilterFactoryTest {

    @Test
    @DisplayName("Redis에 설정을 쓰지 못하면 503을 돌려준다")
    void unavailableWhenWriteFails() {
        var rateLimitSettings = mock(RateLimitSettings.class);
        when(rateLimitSettings.set(any())).thenReturn(Mono.error(new DataAccessResourceFailureException("실패")));
        var problem = mock(ProblemResponse.class);
        when(problem.serviceUnavailable(any())).thenAnswer(invocation -> {
            var exchange = invocation.<ServerWebExchange>getArgument(0);
            exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return Mono.empty();
        });
        var factory = new RateLimitSettingsGatewayFilterFactory(rateLimitSettings, problem,
            ServerCodecConfigurer.create());
        var request = MockServerHttpRequest.put("/api/v1/admin/gateway/rate-limit")
            .contentType(MediaType.APPLICATION_JSON).body("{\"enabled\":false}");
        var exchange = MockServerWebExchange.from(request);

        factory.apply(new Object()).filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
