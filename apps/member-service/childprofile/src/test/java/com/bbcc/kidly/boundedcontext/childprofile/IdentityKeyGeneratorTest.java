package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.IdentityKeyGenerator;
import com.bbcc.kidly.boundedcontext.childprofile.domain.VerifiedIdentity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class IdentityKeyGeneratorTest {
    private final IdentityKeyGenerator generator = new IdentityKeyGenerator("test-secret");
    private final LocalDate birthDate = LocalDate.of(1990, 1, 1);

    @Test
    void normalizesAndGeneratesStableKey() {
        assertThat(generator.generate(identity("홍길동", "01012345678", birthDate)))
            .isEqualTo(generator.generate(identity(" 홍길동 ", "010-1234-5678", birthDate)));
    }

    @Test
    void changesWhenIdentityChanges() {
        var key = generator.generate(identity("홍길동", "01012345678", birthDate));
        assertThat(generator.generate(identity("홍길순", "01012345678", birthDate))).isNotEqualTo(key);
        assertThat(generator.generate(identity("홍길동", "01012345679", birthDate))).isNotEqualTo(key);
        assertThat(generator.generate(identity("홍길동", "01012345678", birthDate.plusDays(1)))).isNotEqualTo(key);
    }

    private VerifiedIdentity identity(String name, String phone, LocalDate date) {
        return new VerifiedIdentity(name, phone, date, LocalDateTime.of(2026, 11, 5, 12, 0));
    }
}
