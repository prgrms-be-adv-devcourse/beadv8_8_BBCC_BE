package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.domain.SocialIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialIdentityTest {

    @Test
    @DisplayName("제공자와 제공자의 사용자 ID가 같으면 같은 소셜 식별자다")
    void equality() {
        assertThat(new SocialIdentity(OAuthProvider.KAKAO, "kakao-1"))
            .isEqualTo(new SocialIdentity(OAuthProvider.KAKAO, "kakao-1"));
    }
}
