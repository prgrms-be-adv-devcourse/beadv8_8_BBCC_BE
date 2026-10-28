package com.bbcc.kidly.boundedcontext.member.domain;

import com.bbcc.kidly.global.error.BusinessException;
import java.util.Arrays;

public enum OAuthProvider {
    KAKAO;

    // URL 경로의 소문자 이름(kakao)으로 찾는다
    public static OAuthProvider from(String name) {
        return Arrays.stream(values())
            .filter(provider -> provider.name().equalsIgnoreCase(name))
            .findFirst()
            .orElseThrow(() -> new BusinessException(MemberErrorCode.UNSUPPORTED_OAUTH_PROVIDER));
    }
}
