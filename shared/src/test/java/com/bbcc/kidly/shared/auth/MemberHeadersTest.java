package com.bbcc.kidly.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemberHeadersTest {

    @Test
    @DisplayName("역할 이름을 쉼표로 잇고, 디코딩하면 같은 역할이다")
    void rolesRoundTrip() {
        var encoded = MemberHeaders.encodeRoles(List.of("BUYER", "SELLER"));

        assertThat(encoded).isEqualTo("BUYER,SELLER");
        assertThat(MemberHeaders.decodeRoles(encoded)).containsExactlyInAnyOrder("BUYER", "SELLER");
    }

    @Test
    @DisplayName("역할이 없으면 빈 문자열이고, 빈 값 · null을 디코딩하면 역할이 없다")
    void emptyRoles() {
        assertThat(MemberHeaders.encodeRoles(List.of())).isEmpty();
        assertThat(MemberHeaders.decodeRoles("")).isEmpty();
        assertThat(MemberHeaders.decodeRoles(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"키들리 엄마", "엄마+아빠", "100%", "키들리 엄마+아빠 100%", "kidly"})
    @DisplayName("닉네임을 인코딩했다가 디코딩하면 원래 닉네임이다(한글 · 공백 · + · %)")
    void nicknameRoundTrip(String nickname) {
        var encoded = MemberHeaders.encodeNickname(nickname);

        assertThat(MemberHeaders.decodeNickname(encoded)).isEqualTo(nickname);
    }

    @Test
    @DisplayName("닉네임은 UTF-8 퍼센트 인코딩이고 공백은 %20, +는 %2B다")
    void encodesNicknameWithPercentEncoding() {
        assertThat(MemberHeaders.encodeNickname("키 +")).isEqualTo("%ED%82%A4%20%2B");
    }

    @ParameterizedTest
    @ValueSource(strings = {"%zz", "abc%", "%E"})
    @DisplayName("잘못된 퍼센트 시퀀스는 IllegalArgumentException이다")
    void rejectsMalformedPercentSequence(String encoded) {
        assertThatThrownBy(() -> MemberHeaders.decodeNickname(encoded))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
