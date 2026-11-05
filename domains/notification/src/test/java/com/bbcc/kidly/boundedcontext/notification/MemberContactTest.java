package com.bbcc.kidly.boundedcontext.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.notification.domain.MemberContact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberContactTest {

    @Test
    @DisplayName("회원 ID와 이메일로 복사본을 만든다")
    void create() {
        var contact = MemberContact.of(1L, "parent@kidly.co.kr");

        assertThat(contact.getMemberId()).isEqualTo(1L);
        assertThat(contact.getEmail()).isEqualTo("parent@kidly.co.kr");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("이메일이 비어 있으면 만들지 않는다")
    void blankEmail(String email) {
        assertThatThrownBy(() -> MemberContact.of(1L, email)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이메일이 320자를 넘으면 만들지 않는다")
    void tooLongEmail() {
        var email = "a".repeat(311) + "@kidly.dev";

        assertThatThrownBy(() -> MemberContact.of(1L, email)).isInstanceOf(IllegalArgumentException.class);
    }
}
