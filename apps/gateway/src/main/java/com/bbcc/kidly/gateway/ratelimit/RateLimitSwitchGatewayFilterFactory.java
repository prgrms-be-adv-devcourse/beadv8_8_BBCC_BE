package com.bbcc.kidly.gateway.ratelimit;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.PrincipalType;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 전체 요청 수 제한 스위치를 보고 바꾸는 관리자 API(application.yml의 RateLimitSwitch 라우트, #1417).
 * 컨트롤러로 만들지 않는다. 컨트롤러 매핑은 라우트보다 먼저 잡혀 AccessTokenFilter · RoleFilter(GlobalFilter)가 돌지 않아
 * 인증 없이 열린다. 라우트 필터로 두면 /api/v1/admin/** 역할 검사를 지난 요청만 여기 온다.
 */
@Slf4j
@Component
class RateLimitSwitchGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final ResolvableType RESPONSE = ResolvableType.forClass(State.class);
    private final RateLimitSwitch rateLimitSwitch;
    private final ProblemResponse problem;
    private final ServerCodecConfigurer codecs;
    private final HttpMessageWriter<State> writer;

    @SuppressWarnings("unchecked")
    RateLimitSwitchGatewayFilterFactory(RateLimitSwitch rateLimitSwitch, ProblemResponse problem,
        ServerCodecConfigurer codecs) {
        this.rateLimitSwitch = rateLimitSwitch;
        this.problem = problem;
        this.codecs = codecs;
        this.writer = (HttpMessageWriter<State>) codecs.getWriters().stream()
            .filter(candidate -> candidate.canWrite(RESPONSE, MediaType.APPLICATION_JSON))
            .findFirst().orElseThrow();
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            if (exchange.getRequest().getMethod() == HttpMethod.GET) {
                return write(exchange, rateLimitSwitch.isEnabled());
            }
            return ServerRequest.create(exchange, codecs.getReaders()).bodyToMono(State.class)
                .onErrorResume(e -> Mono.empty())
                .defaultIfEmpty(new State(null))
                .flatMap(state -> state.enabled() == null ? problem.badRequest(exchange)
                    : change(exchange, state.enabled()));
        };
    }

    private Mono<Void> change(ServerWebExchange exchange, boolean value) {
        return rateLimitSwitch.set(value)
            .flatMap(enabled -> {
                var admin = exchange.getRequest().getHeaders().getFirst(PrincipalHeaders.idHeader(PrincipalType.ADMIN));
                log.info("전체 요청 수 제한 변경. enabled={}, adminId={}", enabled, admin);
                return write(exchange, enabled);
            })
            .onErrorResume(e -> problem.serviceUnavailable(exchange));
    }

    private Mono<Void> write(ServerWebExchange exchange, boolean enabled) {
        return writer.write(Mono.just(new State(enabled)), RESPONSE, MediaType.APPLICATION_JSON,
            exchange.getResponse(), Map.of());
    }

    record State(Boolean enabled) {
    }
}
