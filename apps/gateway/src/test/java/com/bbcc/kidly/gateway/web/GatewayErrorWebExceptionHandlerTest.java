package com.bbcc.kidly.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

/**
 * 예외 타입별로 503 · 504 · 500 중 어디로 보내는지만 본다(#668). ProblemDetail 본문 모양은 통합 테스트(GatewayTestSupport)가 본다.
 * 503 · 504 · 500 로그에는 요청 ID를 key-value traceId로 붙인다(#683).
 */
class GatewayErrorWebExceptionHandlerTest {

    private static final String REQUEST_ID = UUID.randomUUID().toString();

    private final GatewayErrorWebExceptionHandler handler =
        new GatewayErrorWebExceptionHandler(new ProblemResponse(ServerCodecConfigurer.create()));
    private final Logger logger = (Logger) LoggerFactory.getLogger(GatewayErrorWebExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("연결 실패(ConnectException 계열)는 503이고 로그에 요청 ID를 남긴다")
    void connectExceptionIsServiceUnavailable() {
        var exchange = exchange();

        handler.handle(exchange, new ConnectException("Connection refused")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(appender.list.getFirst().getKeyValuePairs()).extracting(p -> p.key, p -> p.value)
            .contains(tuple("traceId", REQUEST_ID));
    }

    @Test
    @DisplayName("백엔드 호스트 이름을 풀지 못하면(UnknownHostException) 503이다")
    void unknownHostIsServiceUnavailable() {
        var exchange = exchange();

        handler.handle(exchange, new UnknownHostException("app")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("응답 시간 초과(NettyRoutingFilter가 바꾼 ResponseStatusException)는 504이고 로그에 요청 ID를 남긴다")
    void responseTimeoutIsGatewayTimeout() {
        var exchange = exchange();
        var timeout = new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Response took longer than timeout");

        handler.handle(exchange, timeout).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(appender.list.getFirst().getKeyValuePairs()).extracting(p -> p.key, p -> p.value)
            .contains(tuple("traceId", REQUEST_ID));
    }

    @Test
    @DisplayName("504가 아닌 ResponseStatusException은 쓰지 않고 그대로 넘긴다")
    void nonGatewayTimeoutResponseStatusExceptionPropagates() {
        var exchange = exchange();
        var methodNotAllowed = new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED);

        assertThatThrownBy(() -> handler.handle(exchange, methodNotAllowed).block()).isSameAs(methodNotAllowed);
    }

    @Test
    @DisplayName("그 밖의 오류는 500이고 로그에 요청 ID를 남긴다")
    void unknownExceptionIsInternalError() {
        var exchange = exchange();

        handler.handle(exchange, new IllegalStateException("boom")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(appender.list.getFirst().getKeyValuePairs()).extracting(p -> p.key, p -> p.value)
            .contains(tuple("traceId", REQUEST_ID));
    }

    @Test
    @DisplayName("이미 응답이 커밋됐으면 쓰지 않고 그대로 넘긴다")
    void propagatesWhenAlreadyCommitted() {
        var exchange = exchange();
        exchange.getResponse().setComplete().block();
        var original = new IllegalStateException("boom");

        assertThatThrownBy(() -> handler.handle(exchange, original).block()).isSameAs(original);
    }

    private MockServerWebExchange exchange() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders"));
        exchange.getAttributes().put(RequestIdFilter.ATTRIBUTE, REQUEST_ID);
        return exchange;
    }
}
