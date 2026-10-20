package com.bbcc.kidly.global.security;

import java.time.Instant;

/**
 * 회원별로 무효화된 Access Token인지 알려 준다. 구현은 회원 도메인이 한다(global은 도메인을 모른다).
 */
public interface AccessTokenRevocations {

    // issuedAt은 JWT iat(초 단위)
    boolean isRevoked(Long memberId, Instant issuedAt);
}
