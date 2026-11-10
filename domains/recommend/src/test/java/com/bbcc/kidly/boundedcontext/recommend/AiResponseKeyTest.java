package com.bbcc.kidly.boundedcontext.recommend;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.recommend.domain.ai.AiResponseKey;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
