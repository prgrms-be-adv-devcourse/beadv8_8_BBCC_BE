package com.bbcc.kidly.boundedcontext.review.in.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 운영 조회 MCP 창구 설정(kidly.community.mcp.*). 값이 틀리면 키 이름이 나오는 오류로 기동에 실패한다.
 *
 * @param enabled 창구를 열지(기본 false, 꺼지면 빈 · 경로 없음)
 * @param maxResults 목록 결과 상한(1~100, 기본 50). 더 큰 size는 상한으로 줄인다
 */
@Validated
@ConfigurationProperties("kidly.community.mcp")
public record CommunityMcpProperties(
    boolean enabled,
    @DefaultValue("50") @Min(1) @Max(100) int maxResults) {
}
