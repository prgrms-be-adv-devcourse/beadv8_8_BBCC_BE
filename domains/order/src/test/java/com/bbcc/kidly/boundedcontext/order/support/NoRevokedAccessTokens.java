package com.bbcc.kidly.boundedcontext.order.support;

import com.bbcc.kidly.global.security.AccessTokenRevocations;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 주문 테스트 앱의 Access Token 무효화 확인(#616). 운영 구현은 회원 도메인에 있어 주문 모듈에는 없다.
 * 주문 테스트는 토큰을 무효화하지 않으므로 늘 "무효화되지 않음"이다.
 */
@Component
public class NoRevokedAccessTokens implements AccessTokenRevocations {

    @Override
    public boolean isRevoked(Long memberId, Instant issuedAt) {
        return false;
    }
}
