package com.bbcc.kidly.gateway.web;

import java.net.URI;
import java.util.Map;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway가 막은 요청에 400 · 401 · 403 ProblemDetail을 쓴다. 본문은 kidly-app(SecurityProblemHandler)과 같아야 한다:
 * 문구는 common CommonErrorCode.INVALID_INPUT · UNAUTHORIZED · FORBIDDEN, 5필드는 common ProblemDetails와 같다.
 * 직렬화는 WebFlux가 쓰는 코덱(Boot JsonMapper)으로 한다.
 */
@Component
public class ProblemResponse {

    // Spring 7은 기본값 about:blank를 응답에서 빼므로 명시해서 5필드를 항상 보낸다
    private static final URI DEFAULT_TYPE = URI.create("about:blank");
    private static final ResolvableType PROBLEM = ResolvableType.forClass(ProblemDetail.class);

    private final HttpMessageWriter<ProblemDetail> writer;

    ProblemResponse(ServerCodecConfigurer codecs) {
        this.writer = problemWriter(codecs);
    }

    public Mono<Void> badRequest(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.BAD_REQUEST, "잘못된 요청입니다", "요청 값을 확인해 주세요.");
    }

    public Mono<Void> unauthorized(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다", "로그인 후 다시 시도해 주세요.");
    }

    public Mono<Void> forbidden(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.FORBIDDEN, "권한이 없습니다", "이 요청을 처리할 권한이 없습니다.");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(DEFAULT_TYPE);
        problem.setTitle(title);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        var response = exchange.getResponse();
        response.setStatusCode(status);
        return writer.write(Mono.just(problem), PROBLEM, MediaType.APPLICATION_PROBLEM_JSON, response, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static HttpMessageWriter<ProblemDetail> problemWriter(ServerCodecConfigurer codecs) {
        return (HttpMessageWriter<ProblemDetail>) codecs.getWriters().stream()
            .filter(writer -> writer.canWrite(PROBLEM, MediaType.APPLICATION_PROBLEM_JSON))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("ProblemDetail을 JSON으로 쓸 코덱이 없습니다"));
    }
}
