package com.bbcc.kidly.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthMemberTest {

    private final AuthMember member = new AuthMember(7L, "회원닉네임");

    @Test
    @DisplayName("문자열로 바꿔도 닉네임을 드러내지 않는다(로그로 샐 수 있다)")
    void toStringHidesNickname() {
        assertThat(member.toString()).contains("7").doesNotContain("회원닉네임");
    }
}
