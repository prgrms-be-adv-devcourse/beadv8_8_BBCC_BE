package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    @DisplayName("원문은 URL-safe Base64 43자(256비트)이고 매번 다르다")
    void generate() {
        var token = RefreshToken.generate();

        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(RefreshToken.generate()).isNotEqualTo(token);
    }

    // "abc"의 SHA-256은 FIPS 180-2 표준 예시 값이다
    @Test
    @DisplayName("토큰 원문을 SHA-256 소문자 hex로 바꾼다")
    void hashOf() {
        assertThat(RefreshToken.hashOf("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
