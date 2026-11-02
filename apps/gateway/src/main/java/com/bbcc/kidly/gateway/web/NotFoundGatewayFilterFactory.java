package com.bbcc.kidly.gateway.web;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * internal 라우트(application.yml)에 쓴다. SetStatus=404 대신 5필드 ProblemDetail로 404를 쓰고 백엔드로 넘기지 않는다(#668).
 */
@Component
@RequiredArgsConstructor
class NotFoundGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private final ProblemResponse problem;

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> problem.notFound(exchange);
    }
}
