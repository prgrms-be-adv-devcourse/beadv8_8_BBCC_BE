package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileErrorCode;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerification;
import com.bbcc.kidly.boundedcontext.childprofile.domain.VerifiedIdentity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class GuardianVerificationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 12, 0);

    @Test
    void rejectsDayBeforeNineteenthBirthday() {
        var identity = identity(NOW.toLocalDate().minusYears(19).plusDays(1));
        assertThatThrownBy(() -> GuardianVerification.create(1L, "key", "transaction", identity, NOW.toLocalDate()))
            .extracting("errorCode").isEqualTo(ChildProfileErrorCode.GUARDIAN_UNDERAGE);
    }

    @Test
    void acceptsNineteenthBirthday() {
        var identity = identity(NOW.toLocalDate().minusYears(19));
        assertThat(GuardianVerification.create(1L, "key", "transaction", identity, NOW.toLocalDate())).isNotNull();
    }

    private VerifiedIdentity identity(LocalDate birthDate) {
        return new VerifiedIdentity("이름", "01012345678", birthDate, NOW);
    }
}
