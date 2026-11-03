package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.auth.domain.AdminPassword;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminPasswordTest {

    @Test
    @DisplayName("12자 이상이면 받는다")
    void acceptsMinLength() {
        assertThat(new AdminPassword("a".repeat(12)).value()).hasSize(12);
    }

    @Test
    @DisplayName("12자보다 짧으면 거절한다")
    void rejectsShort() {
        assertThatThrownBy(() -> new AdminPassword("a".repeat(11)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UTF-8 72바이트까지 받고 73바이트부터 거절한다. 한글은 한 글자가 3바이트다")
    void utf8ByteLimit() {
        assertThat(new AdminPassword("a".repeat(72)).value()).hasSize(72);
        assertThat(new AdminPassword("가".repeat(24)).value()).hasSize(24);

        assertThatThrownBy(() -> new AdminPassword("a".repeat(73))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AdminPassword("가".repeat(25))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("문자열로 바꿔도 원문이 드러나지 않는다")
    void toStringHidesValue() {
        assertThat(new AdminPassword("secret-password-1").toString()).doesNotContain("secret");
    }
}
