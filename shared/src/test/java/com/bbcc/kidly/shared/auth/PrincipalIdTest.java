package com.bbcc.kidly.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PrincipalIdTest {
    @Test
    void memberSubjectStaysBareNumberSoTokensIssuedBeforeAdminsReadAsMembers() {
        assertThat(PrincipalId.parseSubject("42")).isEqualTo(PrincipalId.member(42));
        assertThat(PrincipalId.member(42).toSubject()).isEqualTo("42");
    }

    @Test
    void adminSubjectIsPrefixedSoCodeExpectingNumbersRejectsIt() {
        assertThat(PrincipalId.parseSubject("admin:7")).isEqualTo(PrincipalId.admin(7));
        assertThat(PrincipalId.admin(7).toSubject()).isEqualTo("admin:7");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"member:3", "ADMIN:3", "admin:", "admin:-1", "+3", "0", "admin:0", " 3",
        "9223372036854775808", "admin:x"})
    void rejectsInvalidSubject(String subject) {
        assertThatThrownBy(() -> PrincipalId.parseSubject(subject)).isInstanceOf(IllegalArgumentException.class);
    }
}
