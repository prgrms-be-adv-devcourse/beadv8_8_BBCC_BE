package com.bbcc.kidly.support;

import com.bbcc.kidly.boundedcontext.member.out.AccessTokenIssuer;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 기능 테스트에서 로그인한 회원의 Access Token을 만든다. 예: {@code headers.setBearerAuth(tokens.buyer(1L))}
 * 테스트 클래스패스의 컴포넌트 스캔에 잡혀 통합 · 기능 테스트에서 주입받을 수 있다.
 */
@Component
public class TestTokens {

    private final AccessTokenIssuer accessTokenIssuer;

    public TestTokens(AccessTokenIssuer accessTokenIssuer) {
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public String buyer(Long memberId) {
        return accessTokenIssuer.issue(memberId, List.of("BUYER"));
    }

    public String seller(Long memberId) {
        return accessTokenIssuer.issue(memberId, List.of("BUYER", "SELLER"));
    }

    public String admin(Long memberId) {
        return accessTokenIssuer.issue(memberId, List.of("BUYER", "ADMIN"));
    }
}
