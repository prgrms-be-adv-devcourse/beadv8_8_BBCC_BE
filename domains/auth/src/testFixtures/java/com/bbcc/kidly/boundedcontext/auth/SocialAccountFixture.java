package com.bbcc.kidly.boundedcontext.auth;

import com.bbcc.kidly.boundedcontext.auth.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialAccount;
import com.bbcc.kidly.boundedcontext.auth.domain.SocialIdentity;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

public final class SocialAccountFixture {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);

    private SocialAccountFixture() {
    }

    public static SocialIdentity kakao(String providerUserId) {
        return new SocialIdentity(OAuthProvider.KAKAO, providerUserId);
    }

    public static SocialAccount claimed(String providerUserId) {
        return SocialAccount.claim(kakao(providerUserId), NOW);
    }

    public static SocialAccount linked(Long memberId, String providerUserId) {
        var account = claimed(providerUserId);
        ReflectionTestUtils.setField(account, "memberId", memberId);
        return account;
    }
}
