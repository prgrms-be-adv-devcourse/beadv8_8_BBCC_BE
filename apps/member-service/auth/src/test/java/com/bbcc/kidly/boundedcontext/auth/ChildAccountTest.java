package com.bbcc.kidly.boundedcontext.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.auth.domain.ChildAccount;
import org.junit.jupiter.api.Test;

class ChildAccountTest {
    @Test
    void createsAccount() {
        var account = ChildAccount.create("child1", "hash", 2L, 3L);
        assertThat(account.getLoginId()).isEqualTo("child1");
        assertThat(account.getPasswordHash()).isEqualTo("hash");
        assertThat(account.getGuardianId()).isEqualTo(2L);
        assertThat(account.getChildProfileId()).isEqualTo(3L);
    }
}
