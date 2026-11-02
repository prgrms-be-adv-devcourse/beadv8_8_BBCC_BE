package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SocialAccountTest {

    @Test
    @DisplayName("가입 키는 행 ID에서 만든다")
    void registrationKey() {
        var account = SocialAccountFixture.claimed("kakao-1");
        ReflectionTestUtils.setField(account, "id", 42L);

        assertThat(account.registrationKey()).isEqualTo("social-account:42");
    }

    @Test
    @DisplayName("선점만 한 행은 연결된 회원이 없고, 연결하면 그 회원이다")
    void linkedMemberId() {
        assertThat(SocialAccountFixture.claimed("kakao-1").linkedMemberId()).isEmpty();
        assertThat(SocialAccountFixture.linked(7L, "kakao-1").linkedMemberId()).contains(7L);
    }
}
