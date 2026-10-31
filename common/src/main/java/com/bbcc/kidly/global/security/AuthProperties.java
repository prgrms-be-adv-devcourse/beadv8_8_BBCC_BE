package com.bbcc.kidly.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 서비스 인증 모드(kidly.auth.mode). 체인은 SecurityConfig의 @ConditionalOnProperty가 고르고, 여기서는 값만 검사한다:
 * 오타면 두 체인이 다 빠지고 기본 체인이 떠서 헬스체크는 통과하는데 모든 API가 401이 되므로, 모르는 값이면 기동에 실패하게 한다.
 */
@ConfigurationProperties("kidly.auth")
public record AuthProperties(@DefaultValue("jwt") Mode mode) {

    // 이름은 SecurityConfig의 JWT_MODE · GATEWAY_MODE와 같아야 한다(둘 다 대소문자를 가리지 않는다)
    public enum Mode {
        JWT,
        GATEWAY
    }
}
