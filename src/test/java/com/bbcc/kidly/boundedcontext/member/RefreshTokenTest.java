package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    // "abc"의 SHA-256은 FIPS 180-2 표준 예시 값이다
    @Test
    @DisplayName("토큰 원문을 SHA-256 소문자 hex로 바꾼다")
    void hashOf() {
        assertThat(RefreshToken.hashOf("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
