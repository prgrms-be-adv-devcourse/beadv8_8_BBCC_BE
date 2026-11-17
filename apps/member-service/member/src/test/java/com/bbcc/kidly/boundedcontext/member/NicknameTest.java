package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class NicknameTest {

    @Test
    @DisplayName("제공자 닉네임의 앞뒤 공백을 뺀다")
    void fromProviderStrips() {
        assertThat(Nickname.fromProvider("  하은맘  ")).contains(new Nickname("하은맘"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "가", " 가 "})
    @DisplayName("제공자 닉네임이 없거나 공백뿐이거나 2자 미만이면 비어 있다")
    void fromProviderEmpty(String providerNickname) {
        assertThat(Nickname.fromProvider(providerNickname)).isEmpty();
    }

    @Test
    @DisplayName("제공자 닉네임이 정확히 20자면 그대로 쓴다")
    void fromProviderKeepsTwentyChars() {
        assertThat(Nickname.fromProvider("가".repeat(20))).contains(new Nickname("가".repeat(20)));
    }

    @Test
    @DisplayName("제공자 닉네임이 21자 이상이면 20자로 자른다")
    void fromProviderCuts() {
        assertThat(Nickname.fromProvider("가".repeat(30))).contains(new Nickname("가".repeat(20)));
    }

    @Test
    @DisplayName("자른 끝이 공백이면 뺀다")
    void fromProviderStripsAfterCut() {
        assertThat(Nickname.fromProvider("가".repeat(19) + " 나나")).contains(new Nickname("가".repeat(19)));
    }

    @Test
    @DisplayName("자르는 자리가 서로게이트 쌍의 앞쪽이면 그 앞에서 자른다")
    void fromProviderDoesNotSplitSurrogatePair() {
        assertThat(Nickname.fromProvider("가".repeat(19) + "😀나")).contains(new Nickname("가".repeat(19)));
    }

    @Test
    @DisplayName("숫자 4자리를 붙인다")
    void withNumber() {
        assertThat(new Nickname("소셜회원").withNumber(1234)).isEqualTo(new Nickname("소셜회원1234"));
    }

    @Test
    @DisplayName("숫자를 붙여도 20자를 넘지 않게 앞 16자만 쓴다")
    void withNumberCutsTo16() {
        assertThat(new Nickname("가".repeat(20)).withNumber(1234))
            .isEqualTo(new Nickname("가".repeat(16) + "1234"));
    }

    @Test
    @DisplayName("숫자를 붙일 때 자르는 자리가 서로게이트 쌍의 앞쪽이면 그 앞에서 자른다")
    void withNumberDoesNotSplitSurrogatePair() {
        assertThat(new Nickname("가".repeat(15) + "😀" + "나나").withNumber(1234))
            .isEqualTo(new Nickname("가".repeat(15) + "1234"));
    }
}
