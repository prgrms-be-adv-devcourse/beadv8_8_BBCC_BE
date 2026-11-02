package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.domain.AuthErrorCode;
import com.bbcc.kidly.boundedcontext.member.domain.MemberErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthErrorCodeTest {

    @Test
    @DisplayName("LOGIN_NOT_ALLOWED는 회원 도메인의 것과 status · title · detail이 같다")
    void loginNotAllowedMatchesMember() {
        var auth = AuthErrorCode.LOGIN_NOT_ALLOWED;
        var member = MemberErrorCode.LOGIN_NOT_ALLOWED;

        assertThat(auth.status()).isEqualTo(member.status());
        assertThat(auth.title()).isEqualTo(member.title());
        assertThat(auth.detail()).isEqualTo(member.detail());
    }
}
