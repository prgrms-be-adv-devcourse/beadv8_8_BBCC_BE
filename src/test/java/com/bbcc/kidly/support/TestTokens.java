package com.bbcc.kidly.support;

import com.bbcc.kidly.boundedcontext.member.app.port.AccessTokenIssuer;
import com.bbcc.kidly.global.security.Role;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 기능 테스트에서 로그인한 회원의 Access Token을 만든다. 예: {@code headers.setBearerAuth(tokens.buyer(1L))}
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 통합 · 기능 테스트에서 주입받을 수 있다.
 */
@Component
@RequiredArgsConstructor
public class TestTokens {

    private final AccessTokenIssuer accessTokenIssuer;

    public String buyer(Long memberId) {
        return accessTokenIssuer.issue(memberId, List.of(Role.BUYER));
    }

    public String seller(Long memberId) {
        return accessTokenIssuer.issue(memberId, List.of(Role.BUYER, Role.SELLER));
    }

    public String admin(Long memberId) {
        return accessTokenIssuer.issue(memberId, List.of(Role.BUYER, Role.ADMIN));
    }
}
