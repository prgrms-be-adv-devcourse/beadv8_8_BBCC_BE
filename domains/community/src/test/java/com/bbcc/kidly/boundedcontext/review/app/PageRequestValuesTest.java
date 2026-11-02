package com.bbcc.kidly.boundedcontext.review.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.global.error.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PageRequestValuesTest {

    @Test
    @DisplayName("size: 없으면 20, 1 · 50(경계)은 그대로")
    void sizeBounds() {
        assertThat(PageRequestValues.pageSize(null)).isEqualTo(20);
        assertThat(PageRequestValues.pageSize("1")).isEqualTo(1);
        assertThat(PageRequestValues.pageSize("50")).isEqualTo(50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "51", "-1", "abc", "", "1.5"})
    @DisplayName("size가 1~50 정수가 아니면 INVALID_PAGE_SIZE")
    void invalidSize(String size) {
        assertThatThrownBy(() -> PageRequestValues.pageSize(size)).isInstanceOfSatisfying(BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.INVALID_PAGE_SIZE));
    }

    @Test
    @DisplayName("커서는 (시각, id)를 나노초까지 그대로 왕복하고, URL에 안전한 불투명 문자열이다")
    void cursorRoundTrip() {
        var cursor = new PageRequestValues.Cursor(LocalDateTime.of(2026, 11, 2, 10, 0, 0, 123_456_123), 42L);

        String encoded = cursor.encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(PageRequestValues.Cursor.decode(encoded)).isEqualTo(cursor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"!!", "", "bm90LWEtY3Vyc29y", "MjAyNi0xMS0wMlQxMDowMHxhYmM"})
    @DisplayName("읽을 수 없는 커서(Base64 아님 · 구분자 없음 · id가 숫자 아님)는 INVALID_CURSOR")
    void brokenCursor(String cursor) {
        assertThatThrownBy(() -> PageRequestValues.Cursor.decode(cursor))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ReviewErrorCode.INVALID_CURSOR));
    }
}
