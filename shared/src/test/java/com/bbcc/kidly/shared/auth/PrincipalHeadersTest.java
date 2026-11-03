package com.bbcc.kidly.shared.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PrincipalHeadersTest {
    @Test
    void allHeadersAndPrefixes() {
        assertThat(PrincipalHeaders.ALL).containsExactlyInAnyOrder(MemberHeaders.MEMBER_ID, MemberHeaders.MEMBER_ROLES,
            MemberHeaders.MEMBER_NICKNAME, PrincipalHeaders.ADMIN_ID);
        assertThat(Arrays.stream(PrincipalType.values())
            .flatMap(type -> PrincipalHeaders.namesOf(type).stream()).toList())
            .doesNotHaveDuplicates();
        assertThat(PrincipalHeaders.isPrincipalHeader("x-admin-anything")).isTrue();
        assertThat(PrincipalHeaders.isPrincipalHeader("X-MEMBER-Foo")).isTrue();
        assertThat(PrincipalHeaders.isPrincipalHeader("Authorization")).isFalse();
    }
    @Test
    void idHeadersMatchPrincipalTypes() {
        assertThat(PrincipalHeaders.idHeader(PrincipalType.MEMBER)).isEqualTo(MemberHeaders.MEMBER_ID);
        assertThat(PrincipalHeaders.idHeader(PrincipalType.ADMIN)).isEqualTo(PrincipalHeaders.ADMIN_ID);
    }
}
