package com.bbcc.kidly.gateway.web;

import java.net.URI;
import java.util.Map;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway가 직접 내는 404 · 500 · 503 · 504와 막은 요청의 400 · 401 · 403 · 429에 쓰는 ProblemDetail이다(#608, #668).
 * 400 · 401 · 403 · 404 · 500 문구는 서비스(GlobalExceptionHandler)와 같아야 한다: common CommonErrorCode.INVALID_INPUT ·
 * UNAUTHORIZED · FORBIDDEN · NOT_FOUND · INTERNAL_ERROR, 5필드와 오류 코드(code, 위 상수 이름)는 common ProblemDetails와 같다.
 * 429 · 503 · 504는 common에 없는 코드라 Gateway가 정했다. 직렬화는 WebFlux가 쓰는 코덱(Boot JsonMapper)으로 한다.
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
        return write(exchange, HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 요청입니다", "요청 값을 확인해 주세요.");
    }

    public Mono<Void> unauthorized(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다", "로그인 후 다시 시도해 주세요.");
    }

    public Mono<Void> forbidden(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "권한이 없습니다", "이 요청을 처리할 권한이 없습니다.");
    }

    public Mono<Void> notFound(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없습니다", "요청한 대상을 찾을 수 없습니다.");
    }

    // 요청 수 제한(#816). common에 없는 코드라 Gateway가 정했다. 다시 보내도 되는 때를 Retry-After(초)로 알린다
    public Mono<Void> tooManyRequests(ServerWebExchange exchange, long retryAfterSeconds) {
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return write(exchange, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "요청이 너무 많습니다", "잠시 후 다시 시도해 주세요.");
    }

    public Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "서비스를 잠시 사용할 수 없습니다",
            "잠시 후 다시 시도해 주세요.");
    }

    public Mono<Void> gatewayTimeout(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT", "응답이 지연되고 있습니다", "잠시 후 다시 시도해 주세요.");
    }

    public Mono<Void> internalError(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 오류가 발생했습니다", "잠시 후 다시 시도해 주세요.");
    }

    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(DEFAULT_TYPE);
        problem.setTitle(title);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        problem.setProperty("code", code);
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
