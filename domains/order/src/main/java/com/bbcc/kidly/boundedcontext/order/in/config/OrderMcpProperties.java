package com.bbcc.kidly.boundedcontext.order.in.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 주문 운영 조회 MCP 창구(#925) 설정. 등록은 common의 @ConfigurationPropertiesScan이 한다.
 *
 * @param enabled 켜면 POST /api/v1/admin/orders/mcp 가 열린다. 기본 꺼짐(꺼져 있으면 경로가 없다)
 * @param maxResults 도구 한 번이 돌려주는 목록 줄 수 상한(1~100). 더 큰 size를 받으면 이 값으로 줄인다
 */
@Validated
@ConfigurationProperties("kidly.order.mcp")
public record OrderMcpProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("50") @Min(1) @Max(100) int maxResults) {
}
