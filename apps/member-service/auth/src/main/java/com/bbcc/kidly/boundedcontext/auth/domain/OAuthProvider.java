package com.bbcc.kidly.boundedcontext.auth.domain;

import com.bbcc.kidly.member.support.error.BusinessException;
import java.util.Arrays;

public enum OAuthProvider {
    KAKAO;

    public static OAuthProvider from(String name) {
        return Arrays.stream(values())
            .filter(provider -> provider.name().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> new BusinessException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER));
    }
}
