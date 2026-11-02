package com.bbcc.kidly.gateway.web;

import java.net.ConnectException;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 백엔드 연결 실패 · 응답 시간 초과 · 그 밖의 오류를 503 · 504 · 500 ProblemDetail로 바꾼다(#668).
 * 연결 실패는 java.net.ConnectException 계열(Netty의 연결 시간 초과 ConnectTimeoutException도 그 하위 타입)과
 * java.net.UnknownHostException(compose DNS 이름이 배포 중 잠깐 풀리지 않는 경우)이고,
 * 응답 시간 초과는 NettyRoutingFilter가 바꾼 ResponseStatusException(GATEWAY_TIMEOUT)이다.
 * 504가 아닌 ResponseStatusException(예: actuator의 405)은 쓰지 않고 그대로 넘겨 WebFluxResponseStatusExceptionHandler가
 * 상태 코드만으로 답하게 한다. 응답이 이미 커밋된 뒤의 오류도 쓰지 않고 그대로 넘긴다.
 * order -2: WebFluxResponseStatusExceptionHandler(order 0)보다 먼저 돌아야 504를 ProblemDetail로 쓸 수 있다
 * (늦으면 그 처리기가 본문 없이 504를 낸다).
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ProblemResponse problem;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        var path = exchange.getRequest().getPath().value();
        if (ex instanceof ConnectException || ex instanceof UnknownHostException) {
            log.warn("백엔드 연결에 실패했습니다. path={}, exception={}", path, ex.getClass().getName());
            return problem.serviceUnavailable(exchange);
        }
        if (ex instanceof ResponseStatusException responseStatusException) {
            if (HttpStatus.GATEWAY_TIMEOUT.isSameCodeAs(responseStatusException.getStatusCode())) {
                log.warn("백엔드 응답이 지연되고 있습니다. path={}, exception={}", path, ex.getClass().getName());
                return problem.gatewayTimeout(exchange);
            }
            return Mono.error(ex);
        }
        log.error("Gateway에서 예상하지 못한 오류가 발생했습니다. path={}", path, ex);
        return problem.internalError(exchange);
    }
}
