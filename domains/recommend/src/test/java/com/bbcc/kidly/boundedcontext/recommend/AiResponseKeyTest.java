package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AiResponseKeyTest {

    private static AiResponseKey.Input baseInput() {
        return new AiResponseKey.Input(
            10L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
            BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L), 1);
    }

    @Test
    @DisplayName("고정된 입력의 키는 미리 계산한 SHA-256 값과 같다")
    void keyMatchesPrecomputedHash() {
        // 칸 이름과 값을 줄마다 이어 쓴 문자열(마지막 줄 뒤에 줄바꿈 없음)을 셸의 shasum -a 256으로 구한 값이다.
        // 형식이 바뀌면 저장된 키가 모두 무효가 되므로 이 값이 바뀌면 안 된다.
        AiResponseKey key = AiResponseKey.of(baseInput());

        assertThat(key.value()).isEqualTo("4d092372bb5f88230177cc2003d2186e3775606081ac2f57b99968fc6881d248");
    }

    @Test
    @DisplayName("후보와 장바구니 ID 목록은 순서만 달라도 같은 키다")
    void listOrderDoesNotChangeKey() {
        AiResponseKey.Input shuffled = new AiResponseKey.Input(
            10L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
            BodyType.NORMAL, PreferredFit.REGULAR, List.of(3L, 1L, 2L), List.of(9L, 7L), 1);
        AiResponseKey.Input sorted = new AiResponseKey.Input(
            10L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
            BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L, 9L), 1);

        assertThat(AiResponseKey.of(shuffled)).isEqualTo(AiResponseKey.of(sorted));
    }

    @Test
    @DisplayName("키와 몸무게는 끝자리 0만 달라도 같은 키다")
    void trailingZerosDoNotChangeKey() {
        AiResponseKey.Input withZeros = new AiResponseKey.Input(
            10L, 5L, 36, new BigDecimal("110.0"), new BigDecimal("14.50"),
            BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L), List.of(7L), 1);
        AiResponseKey.Input withoutZeros = new AiResponseKey.Input(
            10L, 5L, 36, new BigDecimal("110"), new BigDecimal("14.5"),
            BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L), List.of(7L), 1);

        assertThat(AiResponseKey.of(withZeros)).isEqualTo(AiResponseKey.of(withoutZeros));
    }

    @Test
    @DisplayName("null 목록과 빈 목록은 같은 키다")
    void nullListEqualsEmptyList() {
        AiResponseKey.Input nullLists = new AiResponseKey.Input(
            10L, null, null, null, null, null, null, null, null, 1);
        AiResponseKey.Input emptyLists = new AiResponseKey.Input(
            10L, null, null, null, null, null, null, List.of(), List.of(), 1);

        assertThat(AiResponseKey.of(nullLists)).isEqualTo(AiResponseKey.of(emptyLists));
    }

    static Stream<Arguments> inputsDifferingInOneField() {
        return Stream.of(
            Arguments.of("상품 ID", new AiResponseKey.Input(
                11L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
                BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L), 1)),
            Arguments.of("자녀 ID", new AiResponseKey.Input(
                10L, 6L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
                BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L), 1)),
            Arguments.of("개월 수", new AiResponseKey.Input(
                10L, 5L, 37, new BigDecimal("95.5"), new BigDecimal("14"),
                BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L), 1)),
            Arguments.of("키", new AiResponseKey.Input(
                10L, 5L, 36, new BigDecimal("96"), new BigDecimal("14"),
                BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L), 1)),
            Arguments.of("몸무게", new AiResponseKey.Input(
                10L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14.5"),
                BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L), 1)),
            Arguments.of("체형", new AiResponseKey.Input(
                10L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
                BodyType.SLIM, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L), 1)),
            Arguments.of("선호 핏", new AiResponseKey.Input(
                10L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
                BodyType.NORMAL, PreferredFit.LOOSE, List.of(1L, 2L, 3L), List.of(7L), 1)),
            Arguments.of("후보 목록", new AiResponseKey.Input(
                10L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
                BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 4L), List.of(7L), 1)),
            Arguments.of("장바구니 목록", new AiResponseKey.Input(
                10L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
                BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L, 8L), 1)),
            Arguments.of("프롬프트 버전", new AiResponseKey.Input(
                10L, 5L, 36, new BigDecimal("95.5"), new BigDecimal("14"),
                BodyType.NORMAL, PreferredFit.REGULAR, List.of(1L, 2L, 3L), List.of(7L), 2))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inputsDifferingInOneField")
    @DisplayName("칸 하나만 달라도 다른 키다")
    void oneDifferentFieldChangesKey(String field, AiResponseKey.Input changed) {
        assertThat(AiResponseKey.of(changed)).isNotEqualTo(AiResponseKey.of(baseInput()));
    }

    @Test
    @DisplayName("값이 빈 칸이 다른 칸으로 밀린 입력은 다른 키다")
    void emptyFieldShiftedToAnotherFieldChangesKey() {
        AiResponseKey.Input productOnly = new AiResponseKey.Input(
            5L, null, null, null, null, null, null, null, null, 1);
        AiResponseKey.Input childOnly = new AiResponseKey.Input(
            null, 5L, null, null, null, null, null, null, null, 1);

        assertThat(AiResponseKey.of(productOnly)).isNotEqualTo(AiResponseKey.of(childOnly));
    }

    @Test
    @DisplayName("목록 안에 null이 있으면 키를 만들지 않는다")
    void nullElementInListIsRejected() {
        List<Long> withNull = Arrays.asList((Long) null);
        AiResponseKey.Input nullCandidate = new AiResponseKey.Input(
            10L, 5L, 36, null, null, null, null, withNull, List.of(), 1);
        AiResponseKey.Input nullCart = new AiResponseKey.Input(
            10L, 5L, 36, null, null, null, null, List.of(), withNull, 1);

        assertThatThrownBy(() -> AiResponseKey.of(nullCandidate)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AiResponseKey.of(nullCart)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
        "",
        "abc",
        "4d092372bb5f88230177cc2003d2186e3775606081ac2f57b99968fc6881d24",
        "4d092372bb5f88230177cc2003d2186e3775606081ac2f57b99968fc6881d2480",
        "4D092372BB5F88230177CC2003D2186E3775606081AC2F57B99968FC6881D248",
        "4d092372bb5f88230177cc2003d2186e3775606081ac2f57b99968fc6881d24g",
        " 4d092372bb5f88230177cc2003d2186e3775606081ac2f57b99968fc6881d24"
    })
    @DisplayName("64자 소문자 hex가 아니면 키를 만들지 않는다")
    void rejectNonHashValue(String value) {
        assertThatThrownBy(() -> new AiResponseKey(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null은 키가 될 수 없다")
    void rejectNullValue() {
        assertThatThrownBy(() -> new AiResponseKey(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "4d092372bb5f88230177cc2003d2186e3775606081ac2f57b99968fc6881d248",
        "0000000000000000000000000000000000000000000000000000000000000000",
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
    })
    @DisplayName("64자 소문자 hex는 키로 받는다")
    void acceptSha256Hex(String value) {
        assertThat(new AiResponseKey(value).value()).isEqualTo(value);
    }
}
