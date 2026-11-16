package com.bbcc.kidly.gateway.ratelimit;

import com.bbcc.kidly.gateway.web.ProblemResponse;
import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.PrincipalType;
import java.time.Duration;
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
 * 전체 요청 수 제한 설정을 보고 바꾸는 관리자 API(application.yml의 RateLimitSettings 라우트, #1417 · #1421).
 * 컨트롤러로 만들지 않는다. 컨트롤러 매핑은 라우트보다 먼저 잡혀 AccessTokenFilter · RoleFilter(GlobalFilter)가 돌지 않아
 * 인증 없이 열린다. 라우트 필터로 두면 /api/v1/admin/** 역할 검사를 지난 요청만 여기 온다.
 * 본문 검증은 여기서 끝낸다. Redis에 잘못된 한도가 들어가지 않게 하려는 것이다.
 */
@Slf4j
@Component
class RateLimitSettingsGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final ResolvableType RESPONSE = ResolvableType.forClass(RateLimitSettings.Snapshot.class);
    private final RateLimitSettings settings;
    private final ProblemResponse problem;
    private final ServerCodecConfigurer codecs;
    private final HttpMessageWriter<RateLimitSettings.Snapshot> writer;

    @SuppressWarnings("unchecked")
    RateLimitSettingsGatewayFilterFactory(RateLimitSettings settings, ProblemResponse problem,
        ServerCodecConfigurer codecs) {
        this.settings = settings;
        this.problem = problem;
        this.codecs = codecs;
        this.writer = (HttpMessageWriter<RateLimitSettings.Snapshot>) codecs.getWriters().stream()
            .filter(candidate -> candidate.canWrite(RESPONSE, MediaType.APPLICATION_JSON))
            .findFirst().orElseThrow();
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            if (exchange.getRequest().getMethod() == HttpMethod.GET) {
                return write(exchange, settings.snapshot());
            }
            return ServerRequest.create(exchange, codecs.getReaders()).bodyToMono(Request.class)
                .onErrorResume(e -> Mono.empty())
                .defaultIfEmpty(new Request(null, null, null))
                .flatMap(request -> request.isValid() ? change(exchange, request.toChange())
                    : problem.badRequest(exchange));
        };
    }

    private Mono<Void> change(ServerWebExchange exchange, RateLimitSettings.Change change) {
        return settings.set(change)
            .flatMap(snapshot -> {
                var admin = exchange.getRequest().getHeaders().getFirst(PrincipalHeaders.idHeader(PrincipalType.ADMIN));
                log.info("요청 수 제한 설정 변경. change={}, adminId={}", change, admin);
                return write(exchange, snapshot);
            })
            .onErrorResume(e -> problem.serviceUnavailable(exchange));
    }

    private Mono<Void> write(ServerWebExchange exchange, RateLimitSettings.Snapshot snapshot) {
        return writer.write(Mono.just(snapshot), RESPONSE, MediaType.APPLICATION_JSON,
            exchange.getResponse(), Map.of());
    }

    record Request(Boolean enabled, Limit client, Limit total) {

        boolean isValid() {
            var anyField = enabled != null || client != null || total != null;
            return anyField && (client == null || client.isValid()) && (total == null || total.isValid());
        }

        RateLimitSettings.Change toChange() {
            return new RateLimitSettings.Change(enabled, client == null ? null : client.toLimit(),
                total == null ? null : total.toLimit());
        }
    }

    record Limit(Integer capacity, Duration period) {

        // Lua가 period를 밀리초로 받아 나누므로 1ms 미만은 막는다. 밀리초가 long을 넘는 값도 저장할 수 없어 막는다
        boolean isValid() {
            if (capacity == null || capacity < 1 || period == null) {
                return false;
            }
            try {
                return period.toMillis() >= 1;
            } catch (ArithmeticException e) {
                return false;
            }
        }

        RateLimitProperties.Limit toLimit() {
            return new RateLimitProperties.Limit(capacity, period);
        }
    }
}
