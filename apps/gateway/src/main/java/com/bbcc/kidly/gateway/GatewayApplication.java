package com.bbcc.kidly.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API Gateway(#608). 클라이언트 요청은 nginx → 여기 → 서비스로 간다.
 * 토큰 검증 · 즉시 무효화 확인 · X-Member-* 헤더 · CORS · 라우팅만 하고 업무 로직은 두지 않는다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
