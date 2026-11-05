package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildPassword;
import org.junit.jupiter.api.Test;

class ChildPasswordTest {

    @Test
    void validatesLengthAndMasksValue() {
        assertThatThrownBy(() -> new ChildPassword("1234567")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ChildPassword("12345678").toString()).doesNotContain("12345678");
        assertThatThrownBy(() -> new ChildPassword("가".repeat(24) + "a"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
