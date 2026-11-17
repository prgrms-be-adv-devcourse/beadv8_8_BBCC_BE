package com.bbcc.kidly.support;

import com.bbcc.kidly.boundedcontext.auth.app.port.OAuthClient;
import com.bbcc.kidly.boundedcontext.auth.app.port.OAuthProfile;
import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.OAuthProvider;
import com.bbcc.kidly.member.support.error.BusinessException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class FakeOAuthClient implements OAuthClient {

    public static final String NICKNAME = "소셜회원";
    public static final String EMAIL = "member@example.com";
    public static final String INVALID_CODE = "invalid-code";
    public static final String NO_NICKNAME_CODE = "no-nickname";
    public static final String NO_EMAIL_CODE = "no-email";
    public static final String SHORT_NICKNAME_CODE = "short-nickname";
    public static final String LONG_NICKNAME_CODE_PREFIX = "long-nickname-";
    public static final String LONG_NICKNAME = "가".repeat(21);

    @Override
    public OAuthProfile fetchProfile(OAuthProvider provider, String code) {
        if (INVALID_CODE.equals(code)) {
            throw new BusinessException(AuthErrorCode.INVALID_AUTHORIZATION_CODE);
        }
        return new OAuthProfile(code, nicknameOf(code), NO_EMAIL_CODE.equals(code) ? null : EMAIL);
    }

    private static String nicknameOf(String code) {
        if (NO_NICKNAME_CODE.equals(code)) {
            return null;
        }
        if (SHORT_NICKNAME_CODE.equals(code)) {
            return "가";
        }
        return code.startsWith(LONG_NICKNAME_CODE_PREFIX) ? LONG_NICKNAME : NICKNAME;
    }
}
