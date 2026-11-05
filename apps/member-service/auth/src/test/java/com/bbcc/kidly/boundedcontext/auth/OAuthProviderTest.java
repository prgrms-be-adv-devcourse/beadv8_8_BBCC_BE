package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.auth.domain.OAuthProvider;
import com.bbcc.kidly.member.support.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OAuthProviderTest {

    @ParameterizedTest
    @ValueSource(strings = {"kakao", "KAKAO", "Kakao"})
    @DisplayName("경로 이름은 대소문자를 구분하지 않고 제공자로 바꾼다")
    void fromIgnoresCase(String name) {
        assertThat(OAuthProvider.from(name)).isEqualTo(OAuthProvider.KAKAO);
    }

    @Test
    @DisplayName("지원하지 않는 제공자면 UNSUPPORTED_OAUTH_PROVIDER")
    void unsupported() {
        assertThatThrownBy(() -> OAuthProvider.from("unknown"))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER));
    }
}
