package com.bbcc.kidly.support;

import com.bbcc.kidly.boundedcontext.member.app.port.OAuthClient;
import com.bbcc.kidly.boundedcontext.member.app.port.OAuthProfile;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.global.error.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 통합 · 기능 테스트에서 소셜 제공자 대신 쓴다. 인가 코드를 그대로 providerUserId로 돌려준다.
 * 실제 카카오 연동은 KakaoOAuthClientTest가 확인한다.
 */
@Primary
@Component
public class FakeOAuthClient implements OAuthClient {

    public static final String NICKNAME = "소셜회원";
    public static final String INVALID_CODE = "invalid-code";
    public static final String NO_NICKNAME_CODE = "no-nickname";

    @Override
    public OAuthProfile fetchProfile(OAuthProvider provider, String code) {
        if (INVALID_CODE.equals(code)) {
            throw new BusinessException(MemberErrorCode.INVALID_AUTHORIZATION_CODE);
        }
        return new OAuthProfile(code, NO_NICKNAME_CODE.equals(code) ? null : NICKNAME);
    }
}
