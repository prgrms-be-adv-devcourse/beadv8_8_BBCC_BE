package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.domain.OAuthProvider;
import com.bbcc.kidly.boundedcontext.member.domain.SocialIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SocialIdentityTest {

    @Test
    @DisplayName("제공자와 제공자의 사용자 ID가 같으면 같은 소셜 식별자다")
    void equality() {
        assertThat(new SocialIdentity(OAuthProvider.KAKAO, "kakao-1"))
            .isEqualTo(new SocialIdentity(OAuthProvider.KAKAO, "kakao-1"));
    }

    @Test
    @DisplayName("제공자나 사용자 ID가 null이면 만들 수 없다")
    void rejectsNull() {
        assertThatThrownBy(() -> new SocialIdentity(null, "kakao-1")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SocialIdentity(OAuthProvider.KAKAO, null))
            .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("사용자 ID가 비어 있으면 만들 수 없다")
    void rejectsBlank(String blank) {
        assertThatThrownBy(() -> new SocialIdentity(OAuthProvider.KAKAO, blank))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
