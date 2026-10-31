package com.bbcc.kidly.gateway.auth;

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
 * 토큰 검증에 실패한 요청에 401 ProblemDetail을 쓴다. 본문은 kidly-app(SecurityProblemHandler)과 같아야 한다:
 * 문구는 common CommonErrorCode.UNAUTHORIZED, 5필드는 common ProblemDetails와 같다.
 * 직렬화는 WebFlux가 쓰는 코덱(Boot JsonMapper)으로 한다.
 */
@Component
class UnauthorizedResponse {

    private static final String TITLE = "로그인이 필요합니다";
    private static final String DETAIL = "로그인 후 다시 시도해 주세요.";
    // Spring 7은 기본값 about:blank를 응답에서 빼므로 명시해서 5필드를 항상 보낸다
    private static final URI DEFAULT_TYPE = URI.create("about:blank");
    private static final ResolvableType PROBLEM = ResolvableType.forClass(ProblemDetail.class);

    private final HttpMessageWriter<ProblemDetail> writer;

    UnauthorizedResponse(ServerCodecConfigurer codecs) {
        this.writer = problemWriter(codecs);
    }

    Mono<Void> write(ServerWebExchange exchange) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, DETAIL);
        problem.setType(DEFAULT_TYPE);
        problem.setTitle(TITLE);
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
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
