package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.app.port.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("받는 사람이 없으면 만들 수 없다")
    void requiresRecipient(String to) {
        assertThatThrownBy(() -> new Email(to, "제목", "본문")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("제목이 없으면 만들 수 없다")
    void requiresSubject(String subject) {
        assertThatThrownBy(() -> new Email("parent@kidly.dev", subject, "본문"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("본문이 없으면 만들 수 없다")
    void requiresText(String text) {
        assertThatThrownBy(() -> new Email("parent@kidly.dev", "제목", text))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
