package com.bbcc.kidly.boundedcontext.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.member.domain.Nickname;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class NicknameTest {

    @ParameterizedTest
    @ValueSource(strings = {"하은", "가나다라마바사아자차카타파하가나다라마바"})
    @DisplayName("2자 이상 20자 이하면 만들 수 있다")
    void acceptsBoundaryLength(String value) {
        assertThat(new Nickname(value).value()).isEqualTo(value);
    }

    @Test
    @DisplayName("null이면 만들 수 없다")
    void rejectsNull() {
        assertThatThrownBy(() -> new Nickname(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "가", "가나다라마바사아자차카타파하가나다라마바사", " 하은맘", "하은맘 "})
    @DisplayName("비어 있거나 2~20자가 아니거나 앞뒤에 공백이 있으면 만들 수 없다")
    void rejectsInvalid(String value) {
        assertThatThrownBy(() -> new Nickname(value)).isInstanceOf(IllegalArgumentException.class);
    }

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

    @ParameterizedTest
    @ValueSource(ints = {1000, 9999})
    @DisplayName("4자리 경계의 숫자는 붙일 수 있다")
    void withNumberAcceptsBoundaryNumber(int number) {
        assertThat(new Nickname("소셜회원").withNumber(number)).isEqualTo(new Nickname("소셜회원" + number));
    }

    @Test
    @DisplayName("숫자를 붙일 때 자르는 자리가 서로게이트 쌍의 앞쪽이면 그 앞에서 자른다")
    void withNumberDoesNotSplitSurrogatePair() {
        assertThat(new Nickname("가".repeat(15) + "😀" + "나나").withNumber(1234))
            .isEqualTo(new Nickname("가".repeat(15) + "1234"));
    }

    @ParameterizedTest
    @ValueSource(ints = {999, 10000})
    @DisplayName("4자리가 아닌 숫자는 붙일 수 없다")
    void withNumberRejectsNotFourDigits(int number) {
        assertThatThrownBy(() -> new Nickname("소셜회원").withNumber(number))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
